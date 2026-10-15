package messages;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class HandshakeMessage {
    private static final String HEADER = "P2PFILESHARINGPROJ";

    public static void send(DataOutputStream out, int myId) throws IOException {
        out.write(HEADER.getBytes(StandardCharsets.US_ASCII));
        out.write(new byte[10]);
        out.writeInt(myId);
        out.flush();
    }

    // return remote peer ID, throws if the header is wrong
    public static int receive(DataInputStream in) throws IOException {
        byte[] header = new byte[18];
        in.readFully(header);
        if (!new String(header, StandardCharsets.US_ASCII).equals(HEADER)) {
            throw new IOException("Bad handshake header");
        }
        in.readFully(new byte[10]);
        return in.readInt();
    }
}