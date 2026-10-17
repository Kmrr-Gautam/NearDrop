package com.neardrop.app.wireless;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransferSocketServer {
    public interface ServerListener {
        void onClientConnected(String clientIp);
        void onTransferProgress(String fileName, long receivedBytes, long totalBytes, String speed);
        void onFileReceived(String fileName, File file, String checksum);
        void onTransferCompleted();
        void onError(String message);
    }

    private ServerSocket serverSocket;
    private final int port;
    private final File destinationDir;
    private final ServerListener listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean isRunning = false;

    public TransferSocketServer(int port, File destinationDir, ServerListener listener) {
        this.port = port;
        this.destinationDir = destinationDir;
        this.listener = listener;
    }

    public synchronized void start() throws IOException {
        if (isRunning) return;
        if (!destinationDir.exists()) {
            destinationDir.mkdirs();
        }
        serverSocket = new ServerSocket(port);
        serverSocket.setReuseAddress(true);
        isRunning = true;

        executor.execute(() -> {
            while (isRunning && !serverSocket.isClosed()) {
                try {
                    Socket client = serverSocket.accept();
                    handleClient(client);
                } catch (IOException e) {
                    if (isRunning && listener != null) {
                        listener.onError("Server socket error: " + e.getMessage());
                    }
                    break;
                }
            }
        });
    }

    private void handleClient(Socket socket) {
        if (listener != null) {
            listener.onClientConnected(socket.getInetAddress().getHostAddress());
        }

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 64 * 1024));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 64 * 1024))) {

            // 1. Handshake
            DirectProtocol.Frame hello = DirectProtocol.readFrame(in);
            if (hello.type != DirectProtocol.TYPE_HELLO) {
                DirectProtocol.writeStringFrame(out, DirectProtocol.TYPE_TRANSFER_REJECT, "Expected HELLO");
                return;
            }
            DirectProtocol.writeStringFrame(out, DirectProtocol.TYPE_CAPABILITIES, "NearDrop-V2-StreamEngine");

            // 2. Read Files
            while (isRunning) {
                DirectProtocol.Frame frame = DirectProtocol.readFrame(in);
                if (frame.type == DirectProtocol.TYPE_TRANSFER_COMPLETED) {
                    if (listener != null) listener.onTransferCompleted();
                    break;
                }

                if (frame.type == DirectProtocol.TYPE_FILE_METADATA) {
                    // Payload format: "fileName:totalBytes:sha256"
                    String meta = frame.getPayloadAsString();
                    String[] parts = meta.split(":", 3);
                    String fileName = parts[0];
                    long totalBytes = Long.parseLong(parts[1]);
                    String expectedHash = parts.length > 2 ? parts[2] : "";

                    File targetFile = new File(destinationDir, fileName);
                    File tempFile = new File(destinationDir, fileName + ".part");

                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    long receivedBytes = 0;
                    long startTime = System.currentTimeMillis();

                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        DirectProtocol.writeFrame(out, DirectProtocol.TYPE_TRANSFER_ACCEPT, null);

                        byte[] buffer = new byte[64 * 1024];
                        while (receivedBytes < totalBytes) {
                            DirectProtocol.Frame chunkFrame = DirectProtocol.readFrame(in);
                            if (chunkFrame.type != DirectProtocol.TYPE_FILE_DATA) {
                                break;
                            }
                            fos.write(chunkFrame.payload);
                            digest.update(chunkFrame.payload);
                            receivedBytes += chunkFrame.payload.length;

                            long elapsed = Math.max(1, System.currentTimeMillis() - startTime);
                            double speedMbps = (receivedBytes * 8.0) / (elapsed * 1000.0);
                            String speedStr = String.format("%.1f MB/s", (receivedBytes / (1024.0 * 1024.0)) / (elapsed / 1000.0));

                            if (listener != null) {
                                listener.onTransferProgress(fileName, receivedBytes, totalBytes, speedStr);
                            }
                        }
                    }

                    // Rename temp file to target
                    if (tempFile.exists()) {
                        tempFile.renameTo(targetFile);
                    }

                    byte[] hashBytes = digest.digest();
                    StringBuilder sb = new StringBuilder();
                    for (byte b : hashBytes) {
                        sb.append(String.format("%02x", b));
                    }
                    String computedHash = sb.toString();

                    DirectProtocol.writeStringFrame(out, DirectProtocol.TYPE_FILE_COMPLETED, computedHash);

                    if (listener != null) {
                        listener.onFileReceived(fileName, targetFile, computedHash);
                    }
                }
            }
        } catch (Exception e) {
            if (listener != null) {
                listener.onError("Client transfer failed: " + e.getMessage());
            }
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    public synchronized void stop() {
        isRunning = false;
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (Exception ignored) {}
            serverSocket = null;
        }
        executor.shutdownNow();
    }
}
