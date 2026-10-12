import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*
import java.util.*;

public class Peer {
    private final int myId;
    private final String host;
    private final int port;
    private int hasFile;
    private final Path peerDir;
    // List of all peers that this peer will attempt to connect with (In project example, 1003 will connect with 1002 and 1001)
    private ArrayList<int> peersToConnect;

    public Peer(int myId) {
        this.myId = myId;
        // Peer directories are located in root project directory
        this.peerDir = "../../peer_" + myId;
        this.peersToConnect = new ArrayList<>();
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
            } catch (IOException e) {
                System.err.println("Error reading the PeerInfo config file: " + e.getMessage());
            }
        }
    }
}