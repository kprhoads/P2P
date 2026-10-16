package client;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * log generator for when actions are performed by a peer, including connections, config changes, etc.
 * outputs to a file 'log_peer_[peerID].log'
 * calls log (write) function via object functions
 */
public class PeerLogger {
    // send LocalDateTime.now() here to format into month-day-year hour:minute:second
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm:ss");

    // id of the peer being affected (receiver / initiates action)
    private final int myId;
    private final BufferedWriter writer;

    // constructor
    public PeerLogger(int myId) throws IOException {
        this.myId = myId;
        // newBufferWriter truncates existing file
        this.writer = Files.newBufferedWriter(
                Paths.get("log_peer_" + myId + ".log"), StandardCharsets.UTF_8);
    }

    // helper, write function to newBufferedWriter with date format at front automatically
    private synchronized void log(String message) {
        String line = "[" + LocalDateTime.now().format(TIME_FORMATTER) + "]: " + message;
        try {
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("Logging failed: " + e.getMessage());
        }
        // console debug, comment out if necessary
        System.out.println(line);

    }

    // quiet close
    public synchronized void close() { try { writer.close(); } catch (IOException ignored) {} }

    // event: TCP connection made to peer
    public void connectionMade(int otherId) { log("Peer [" + myId + "] makes a connection to Peer [" + otherId + "]."); }

    // event: TCP connection made from peer
    public void connectionReceived(int otherId) { log("Peer [" + myId + "] is connected from Peer [" + otherId + "]."); }

    // event: peer changes preferred neighbors
    public void preferredNeighborsChanged(List<Integer> neighborIds) {
        // [preferred neighbor list] is the list of peer IDs separated by comma ','... implementation subject to change
        StringBuilder list = new StringBuilder();
        // loop through neighborIds
        for (int i = 0; i < neighborIds.size(); i++) {
            if (i > 0) { list.append(","); }
            list.append(neighborIds.get(i));
        }
        log("Peer [" + myId + "] has the preferred neighbors [" + list + "].");
    }

    // event: peer changes optimistically unchoked neighbor
    public void optimisticNeighborChanged(int neighborId) { log("Peer [" + myId + "] has the optimistically unchoked neighbor [" + neighborId + "]."); }

    // event: neighbor unchokes peer
    public void unchokedBy(int otherId) { log("Peer [" + myId + "] is unchoked by [" + otherId + "]."); }

    // event: neighbor chokes peer
    public void chokedBy(int otherId) { log("Peer [" + myId + "] is choked by [" + otherId + "]."); }

    // event: peer receives a 'have' message from sender peer
    public void receivedHave(int fromId, int pieceIndex) { log("Peer [" + myId + "] received the 'have' message from [" + fromId + "] for the piece [" + pieceIndex + "]."); }

    // event: peer receives an 'interested' message from sender peer
    public void receivedInterested(int fromId) { log("Peer [" + myId + "] received the 'interested' message from [" + fromId + "]."); }

    // event: peer receives a 'not interested' message
    public void receivedNotInterested(int fromId) { log("Peer [" + myId + "] received the 'not interested' message from [" + fromId + "]."); }

    // event: peer finishes downloading a piece
    public void pieceDownloaded(int fromId, int pieceIndex, int piecesNowOwned) {
        log("Peer [" + myId + "] has downloaded the piece [" + pieceIndex + "] from [" + fromId
                + "]. Now the number of pieces it has is [" + piecesNowOwned + "].");
    }

    // event: peer downloads the complete file
    public void downloadComplete() { log("Peer [" + myId + "] has downloaded the complete file."); }

}
