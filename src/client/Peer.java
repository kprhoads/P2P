package client;
import messages.Message;
import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Peer {
    private static final byte[] HANDSHAKE_HEADER = "P2PFILESHARINGPROJ".getBytes(StandardCharsets.US_ASCII);

    private final int myId;
    private String host;
    private int port;
    private int hasFile;
    private final Path peerDir;

    // Common.cfg data
    private int numPreferredNeighbors;
    private int unchokingInterval;
    private int optimisticUnchokingInterval;
    private String fileName;
    private long fileSize;
    private int pieceSize;
    private int numPieces;

    // List of all peers that this peer will attempt to connect with (In project example, 1003 will connect with 1002 and 1001)
    private ArrayList<Integer> peersToConnect;
    // List containing all Connection objects for this peer
    private ArrayList<Connection> connections;

    public Peer(int myId) {
        this.myId = myId;
        // Peer directories are located in root project directory
        this.peerDir = Paths.get("peer_" + myId);
        this.peersToConnect = new ArrayList<>();
        this.connections = new ArrayList<>();
    }

    private int doHandshake(Socket s) throws IOException {
        DataOutputStream dout = new DataOutputStream(s.getOutputStream());
        DataInputStream din = new DataInputStream(s.getInputStream());

        // send handshake to connected peer
        dout.write(HANDSHAKE_HEADER);
        dout.write(new byte[10]);
        dout.writeInt(myId);

        // receive handshake from connected peer
        byte[] peer_header = new byte[18];
        din.readFully(peer_header);
        din.readFully(new byte[10]);
        int peerId = din.readInt();

        // check if the header received matches
        if (!Arrays.equals(peer_header, HANDSHAKE_HEADER)) {
            throw new IOException("Invalid handshake header from connected peer");
        }
        return peerId;
    }

    private void readPeerInfo() {
        /*
         * Helper function for reading in the required information from the PeerInfo.cfg file:
         *   - Peers connected before this peer to populate peersToConnect
         *   - The host name
         *   - The port number
         *   - Whether this peer has the complete file
         */
        try {
            List<String> lines = Files.readAllLines(Paths.get("PeerInfo.cfg"));

            for (String line : lines) {
                line = line.trim();
                // skip empty lines
                if (line.isEmpty()) {
                    continue;
                }

                String[] content = line.split(" ");
                if (Integer.parseInt(content[0]) == this.myId) {
                    // We've reached this peer in the config file, get the host name, port number, and file status
                    this.host = content[1];
                    this.port = Integer.parseInt(content[2]);
                    this.hasFile = Integer.parseInt(content[3]);

                    // We don't need to track peers after this one, break the loop
                    break;
                } else {
                    // The current line contains a peer we will need to connect to. Add to peersToConnect
                    this.peersToConnect.add(Integer.parseInt(content[0]));
                }
            }
        } catch(IOException e){
            System.err.println("Error reading the PeerInfo config file: " + e.getMessage());
        }
    }

    private void readCommonConfig() {
        /*
         * Helper function for reading in the required information from the Common.cfg file:
         *   - Number of preferred neighbors
         *   - Unchoking interval
         *   - Optimistic unchoking interval
         *   - File name in which all peers are interested
         *   - File size in bytes
         *   - Piece size in bytes
         */
        try {
            List<String> lines = Files.readAllLines(Paths.get("Common.cfg"));

            for (String line : lines) {
                line = line.trim();
                // skip empty lines
                if (line.isEmpty()) {
                    continue;
                }

                String[] content  = line.split("\\s+", 2);
                // check each property needed, break after each is written
                switch (content[0]) {
                    case "NumberOfPreferredNeighbors":
                        this.numPreferredNeighbors = Integer.parseInt(content[1]);
                        break;
                    case "UnchokingInterval":
                        this.unchokingInterval = Integer.parseInt(content[1]);
                        break;
                    case "OptimisticUnchokingInterval":
                        this.optimisticUnchokingInterval = Integer.parseInt(content[1]);
                        break;
                    case "FileName":
                        this.fileName = content[1];
                        break;
                    case "FileSize":
                        this.fileSize = Long.parseLong(content[1]);
                        break;
                    case "PieceSize":
                        this.pieceSize = Integer.parseInt(content[1]);
                        break;
                }
            }
            // number of pieces, rounding up one to avoid lost data
            numPieces = (int) ((fileSize + pieceSize - 1) / pieceSize);
        } catch (IOException e) {
            System.err.println("Error reading the Common config file: " + e.getMessage());
        }
    }

    private void startListening() {
        // We need a thread to constantly listen for new connections
        Thread t = new Thread(() -> {
            try (ServerSocket listener = new ServerSocket(port)) {
                System.out.println("Listening on port " + port);
                while (true) {
                    Socket s = listener.accept();
                    try {
                        connectPeer(s);
                    } catch (IOException e){
                        System.err.println("Peer connection rejected: " + e.getMessage());
                        s.close();
                    }
                }
            } catch (IOException e) {
                System.err.println("Listener stopped: " + e.getMessage());
            }
        });
        t.setDaemon(true);  // Thread exits when main thread ends
        t.start();
    }

    private void connectPeer(Socket s) throws IOException {
        // Send handshake to accept remote peer connection
        int peerId = doHandshake(s);
        Connection c = new Connection(s, peerId);
        connections.add(c);
        c.start();
        System.out.println("Connected peer " + peerId + " on socket " + s.getRemoteSocketAddress());
    }

    private void connectTo(String host, int port, int expectedId) throws IOException {
        // Connect to remote peer and send handshake
        Socket s = null;
        try {
            s = new Socket(host, port);
            // ID the other side actually sent
            int remoteId = doHandshake(s);
            // check if passed ID matches the ID returned from handshake, freak out if no
            if (remoteId != expectedId) {
                throw new IOException("Handshake ID mismatch: expected " + expectedId + ", got " + remoteId);
            }
            Connection c = new Connection(s, remoteId);
            connections.add(c);
            c.start();
            System.out.println("Connected to peer " + remoteId + " on socket " + s.getRemoteSocketAddress());
        } catch (IOException e) {
            System.err.println("Could not connect to " + host + " on port " + port + ": " + e.getMessage());
            if (s != null) {
                // close quietly
                try {
                    s.close();
                } catch (IOException ignored) {}
            }
        }
    }

    /*
     * A connection thread class.  Connections are spawned from the listening
     * loop and are responsible for dealing with a single P2P connection.
     * Adapted from project Sample Server.java file, will need to change
     * for file transfer instead of message exchange.
     */
    private static class Connection extends Thread {
        private String message;    //message received from the peer
        private String MESSAGE;    //uppercase message send to the peer
        private Socket connection;
        private ObjectInputStream in;    //stream read from the socket
        private ObjectOutputStream out;    //stream write to the socket
        private int peerID;        //The ID of the connected peer

        public Connection(Socket connection, int peerID) throws IOException {
            this.connection = connection;
            this.peerID = peerID;

            //initialize Input and Output streams
            out = new ObjectOutputStream(connection.getOutputStream());
            out.flush();
            in = new ObjectInputStream(connection.getInputStream());
        }

        public void run() {
            try {
                while (true) {
                    //receive the message sent from the peer
                    message = (String) in.readObject();
                    //show the message to the user
                    System.out.println("Receive message: " + message + " from peer " + peerID);
                    //Capitalize all letters in the message
                    MESSAGE = message.toUpperCase();
                    //send MESSAGE back to the peer
                    sendMessage(MESSAGE);
                }
            } catch (ClassNotFoundException | IOException classnot) {
                System.err.println("Data received in unknown format");
            } finally {
                //Close connections
                try {
                    in.close();
                    out.close();
                    connection.close();
                } catch (IOException ioException) {
                    System.out.println("Disconnect with Peer " + peerID);
                }
            }
        }

        public void sendMessage(String msg) {
            // Will need to be modified for file transfer, this is basic P2P connection testing
            try{
                out.writeObject(msg);
                out.flush();
                System.out.println("Send message: " + msg + " to Peer " + peerID);
            }
            catch(IOException ioException){
                ioException.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        int id = Integer.parseInt(args[0]);
        Peer p = new Peer(id);
        p.readPeerInfo();
        p.readCommonConfig();
        System.out.println("host=" + p.host + " port=" + p.port + " hasFile=" + p.hasFile + " connectTo=" + p.peersToConnect);
        System.out.println("numPreferredNeighbors=" + p.numPreferredNeighbors + "unchokingInterval=" + p.unchokingInterval +
                "optimisticUnchokingInterval="+ p.optimisticUnchokingInterval + "fileName=" + p.fileName +
                "fileSize=" + p.fileSize + "pieceSize" + p.pieceSize);
    }
}