package messages;
import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

/**
 * an "actual" message of the p2p protocol after the handshake
 * - length of the message (4 bytes) (does not count itself)
 * - type of the message (1 byte, number from 0-7) contained in a byte "type"
 * - actual payload contained in a byte[] "payload"
 * the existence of the payload varies with the message type, empty payload != null
 * wire format: [4-byte length][1-byte type][payload]
 */

public class Message {
    // message types in project spec
    public static final byte choke = 0;
    public static final byte unchoke = 1;
    public static final byte interested = 2;
    public static final byte not_interested = 3;
    public static final byte have = 4;
    public static final byte bitfield = 5;
    public static final byte request = 6;
    public static final byte piece = 7;
    private final byte type;
    // payload can be empty, but not null
    private final byte[] payload;

    // constructor
    public Message(byte type, byte[] payload) {
        this.type = type;
        if (payload == null) {
            this.payload = new byte[0];
        }
        else {
            this.payload = payload;
        }
    }
    // getters
    public byte getType() {return type;}
    public byte[] getPayload() {return payload;}

    // build outgoing messages, no payload
    public static Message choke() { return new Message(choke, null); }
    public static Message unchoke() { return new Message(unchoke, null); }
    public static Message interested() { return new Message(interested, null); }
    public static Message notInterested() { return new Message(not_interested, null); }

    // ‘have’ messages have a payload that contains a 4-byte piece index field
    public static Message have(int pieceIndex) {
        // need to encode pieceIndex as 4 bytes in big-endian
        // java.nio.* provides ByteBuffer to do bit operations, default is big-endian
        byte[] buffer = ByteBuffer.allocate(4).putInt(pieceIndex).array();
        return new Message(have, buffer);
    }

    // ‘bitfield’ messages have a bitfield as its payload
    // piece 0 = high bit of byte 0, spare bits = 0
    public static Message bitfield(byte[] bits) {
        return new Message(bitfield, bits);
    }

    // ‘request’ messages have a payload which consists of a 4-byte piece index field
    // don't divide a piece into smaller sub pieces
    public static Message request(int pieceIndex) {
        // exact same encoding for have() lol
        byte[] buffer = ByteBuffer.allocate(4).putInt(pieceIndex).array();
        return new Message(request, buffer);
    }

    // ‘piece’ messages have a payload which consists of a 4-byte piece index field and the
    //content of the piece
    public static Message piece(int pieceIndex, byte[] data) {
        byte[] pieceBuffer = ByteBuffer.allocate(4 + data.length).putInt(pieceIndex).put(data).array();
        return new Message(piece, pieceBuffer);
    }

    // gets the index / data for 'piece' messages, don't wanna write a type checker so be responsible
    public int getPieceIndex() {
        return ByteBuffer.wrap(payload, 0, 4).getInt();
    }

    public byte[] getPieceData() {
        return Arrays.copyOfRange(payload, 4, payload.length);
    }

    public byte[] toBytes() {
        // write length, type, payload
        return ByteBuffer.allocate(4 + 1 + payload.length).putInt(1 + payload.length).put(type).put(payload).array();
    }

    // ObjectInputStream and ObjectOutputStream won't work, need raw bytes
    // get raw bytes with DataInputStream and DataOutputStream
    public void writeTo(DataOutputStream out) throws IOException {
        // many threads may send to same peer (scary) so flush bytes to not interleave with others
        synchronized (out) { // this is a keyword apparently lol
            out.write(toBytes());
            out.flush();
        }
    }

    public static Message readFrom(DataInputStream in) throws IOException {
        int length = in.readInt(); // blocks
        // disconnect should return EOFException
        if (length < 1) {
            throw new IOException("Invalid message length " + length);
        }

        byte type = in.readByte();
        byte[] readPayload = new byte[length - 1];
        in.readFully(readPayload);
        return new Message(type, readPayload);
    }

    @Override
    public String toString() {
        return "Message{type=" + type + ", payloadLength=" + payload.length + "}";
    }

}