package com.neardrop.app.wireless;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class DirectProtocol {
    public static final int MAGIC_HEADER = 0x4E445250; // "NDRP"
    public static final int PROTOCOL_VERSION = 2;

    // Message Types
    public static final byte TYPE_HELLO = 0x01;
    public static final byte TYPE_CAPABILITIES = 0x02;
    public static final byte TYPE_SESSION_CHALLENGE = 0x03;
    public static final byte TYPE_SESSION_AUTH = 0x04;
    public static final byte TYPE_TRANSFER_REQUEST = 0x05;
    public static final byte TYPE_TRANSFER_ACCEPT = 0x06;
    public static final byte TYPE_TRANSFER_REJECT = 0x07;
    public static final byte TYPE_FILE_METADATA = 0x08;
    public static final byte TYPE_FILE_DATA = 0x09;
    public static final byte TYPE_FILE_ACK = 0x0A;
    public static final byte TYPE_FILE_COMPLETED = 0x0B;
    public static final byte TYPE_TRANSFER_COMPLETED = 0x0C;
    public static final byte TYPE_TRANSFER_CANCEL = 0x0D;
    public static final byte TYPE_PING = 0x0E;
    public static final byte TYPE_PONG = 0x0F;

    public static class Frame {
        public byte type;
        public byte[] payload;

        public Frame(byte type, byte[] payload) {
            this.type = type;
            this.payload = payload != null ? payload : new byte[0];
        }

        public String getPayloadAsString() {
            return new String(payload, StandardCharsets.UTF_8);
        }
    }

    /**
     * Writes a length-prefixed binary frame: [MAGIC 4B][VERSION 1B][TYPE 1B][LENGTH 4B][PAYLOAD NB]
     */
    public static void writeFrame(DataOutputStream out, byte type, byte[] payload) throws IOException {
        out.writeInt(MAGIC_HEADER);
        out.writeByte(PROTOCOL_VERSION);
        out.writeByte(type);
        int len = payload != null ? payload.length : 0;
        out.writeInt(len);
        if (len > 0) {
            out.write(payload);
        }
        out.flush();
    }

    public static void writeStringFrame(DataOutputStream out, byte type, String text) throws IOException {
        byte[] bytes = text != null ? text.getBytes(StandardCharsets.UTF_8) : new byte[0];
        writeFrame(out, type, bytes);
    }

    /**
     * Reads a validated length-prefixed binary frame
     */
    public static Frame readFrame(DataInputStream in) throws IOException {
        int magic = in.readInt();
        if (magic != MAGIC_HEADER) {
            throw new IOException("Invalid protocol magic header: 0x" + Integer.toHexString(magic));
        }
        int version = in.readByte();
        if (version != PROTOCOL_VERSION && version != 1) {
            throw new IOException("Unsupported protocol version: " + version);
        }
        byte type = in.readByte();
        int length = in.readInt();
        if (length < 0 || length > 16 * 1024 * 1024) { // Max 16MB control frame
            throw new IOException("Invalid frame payload size: " + length);
        }
        byte[] payload = new byte[length];
        in.readFully(payload);
        return new Frame(type, payload);
    }
}
