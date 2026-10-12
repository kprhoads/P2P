import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

public class Peer {
    private final int myId;
    private final String host;
    private final int port;
    private int hasFile;
    private final Path peerDir;
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

    private void readPeerInfo() {
        /*
         * Helper function for reading in the required information from the PeerInfo.cfg file:
         *   - Peers connected before this peer to populate peersToConnect
         *   - The host name
         *   - The port number
         *   - Whether this peer has the complete file
         */
        try {
            List<String> lines = Files.readAllLines(Paths.get("../../PeerInfo.cfg"));

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
            } catch(IOException e){
                System.err.println("Error reading the PeerInfo config file: " + e.getMessage());
            }
        }
    }

    private void startListening() {
        // We need a thread to constantly listen for new connections
        Thread t = new Thread(() -> {
            try (ServerSocket listener = new ServerSocket(this.port)) {
                System.out.println("Listening on port " + this.port);
                while (true) {
                    Socket connection = listener.accept();
                    /*
                     * We need to do a handshake here to determine the ID of the peer connecting
                     * and create a Connection object, appending to the connections array.
                     */
                }
            } catch (IOException e) {
                System.err.println("Listener stopped: " + e.getMessage());
            }
        });
        t.setDaemon(true);  // Thread exits when main thread ends
        t.start();

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

        public Connection(Socket connection, int peerID) {
            this.connection = connection;
            this.peerID = peerID;
        }

        public void run() {
            try {
                //initialize Input and Output streams
                out = new ObjectOutputStream(connection.getOutputStream());
                out.flush();
                in = new ObjectInputStream(connection.getInputStream());
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
                } catch (ClassNotFoundException classnot) {
                    System.err.println("Data received in unknown format");
                }
            } catch (IOException ioException) {
                System.out.println("Disconnect with Peer " + peerID);
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
}