package com.neardrop.app.wireless;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

public class TransferSocketClient {
    public interface ClientTransferListener {
        void onProgress(String fileName, long sentBytes, long totalBytes, String speed);
        void onFileCompleted(String fileName);
        void onAllCompleted();
        void onError(String message);
    }

    public static void sendFiles(String host, int port, List<File> files, ClientTransferListener listener) {
        new Thread(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 8000);
                socket.setTcpNoDelay(true);

                try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 64 * 1024));
                     DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 64 * 1024))) {

                    // 1. Handshake
                    DirectProtocol.writeStringFrame(out, DirectProtocol.TYPE_HELLO, "NearDrop-V2-Sender");
                    DirectProtocol.Frame caps = DirectProtocol.readFrame(in);
                    if (caps.type != DirectProtocol.TYPE_CAPABILITIES) {
                        if (listener != null) listener.onError("Receiver rejected handshake");
                        return;
                    }

                    // 2. Stream Files
                    byte[] buffer = new byte[64 * 1024];
                    for (File file : files) {
                        long totalBytes = file.length();
                        String fileName = file.getName();

                        // Send Metadata
                        DirectProtocol.writeStringFrame(out, DirectProtocol.TYPE_FILE_METADATA, fileName + ":" + totalBytes + ":");

                        // Wait for accept
                        DirectProtocol.Frame acceptFrame = DirectProtocol.readFrame(in);
                        if (acceptFrame.type != DirectProtocol.TYPE_TRANSFER_ACCEPT) {
                            if (listener != null) listener.onError("Receiver rejected file: " + fileName);
                            return;
                        }

                        long sentBytes = 0;
                        long startTime = System.currentTimeMillis();

                        try (FileInputStream fis = new FileInputStream(file)) {
                            int read;
                            while ((read = fis.read(buffer)) != -1) {
                                byte[] chunk = (read == buffer.length) ? buffer : Arrays.copyOf(buffer, read);
                                DirectProtocol.writeFrame(out, DirectProtocol.TYPE_FILE_DATA, chunk);
                                sentBytes += read;

                                long elapsed = Math.max(1, System.currentTimeMillis() - startTime);
                                String speedStr = String.format("%.1f MB/s", (sentBytes / (1024.0 * 1024.0)) / (elapsed / 1000.0));

                                if (listener != null) {
                                    listener.onProgress(fileName, sentBytes, totalBytes, speedStr);
                                }
                            }
                        }

                        // Wait for File Completed ACK
                        DirectProtocol.Frame compFrame = DirectProtocol.readFrame(in);
                        if (compFrame.type == DirectProtocol.TYPE_FILE_COMPLETED) {
                            if (listener != null) listener.onFileCompleted(fileName);
                        }
                    }

                    // 3. Complete Transfer
                    DirectProtocol.writeFrame(out, DirectProtocol.TYPE_TRANSFER_COMPLETED, null);
                    if (listener != null) listener.onAllCompleted();
                }
            } catch (Exception e) {
                if (listener != null) {
                    listener.onError("Transfer failed: " + e.getMessage());
                }
            }
        }).start();
    }
}
