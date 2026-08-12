package com.gnurushev.fileprocessor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class SingleInstanceService implements AutoCloseable {
    private static final int CONNECT_TIMEOUT_MILLIS = 450;

    private final int port;
    private final ExecutorService serverExecutor = Executors.newSingleThreadExecutor(new ServerThreadFactory());
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocket serverSocket;

    public SingleInstanceService(int port) {
        this.port = port;
    }

    public boolean forwardToRunningInstance(Path file) {
        String payload = file == null
            ? MessageType.ACTIVATE.name()
            : MessageType.OPEN_FILE.name() + "|" + file.toAbsolutePath().normalize();

        try (Socket socket = new Socket()) {
            socket.setOption(StandardSocketOptions.TCP_NODELAY, true);
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), CONNECT_TIMEOUT_MILLIS);
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
                writer.println(payload);
                return true;
            }
        } catch (IOException ignored) {
            return false;
        }
    }

    public void start(Consumer<IncomingMessage> handler) throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
        running.set(true);
        serverExecutor.submit(() -> acceptLoop(handler));
    }

    private void acceptLoop(Consumer<IncomingMessage> handler) {
        while (running.get()) {
            try (Socket socket = serverSocket.accept();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                IncomingMessage message = IncomingMessage.parse(reader.readLine());
                if (message != null) {
                    handler.accept(message);
                }
            } catch (IOException error) {
                if (running.get()) {
                    System.err.println("Single-instance listener stopped: " + error.getMessage());
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        running.set(false);
        if (serverSocket != null) {
            serverSocket.close();
        }
        serverExecutor.shutdownNow();
    }

    public enum MessageType {
        ACTIVATE,
        OPEN_FILE
    }

    public record IncomingMessage(MessageType type, Path path) {
        public static IncomingMessage parse(String rawMessage) {
            if (rawMessage == null || rawMessage.isBlank()) {
                return null;
            }
            if (rawMessage.equals(MessageType.ACTIVATE.name())) {
                return new IncomingMessage(MessageType.ACTIVATE, null);
            }
            if (rawMessage.startsWith(MessageType.OPEN_FILE.name() + "|")) {
                String pathPart = rawMessage.substring(MessageType.OPEN_FILE.name().length() + 1);
                return new IncomingMessage(MessageType.OPEN_FILE, Paths.get(pathPart));
            }
            return null;
        }
    }

    private static final class ServerThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "file-processor-instance-listener");
            thread.setDaemon(true);
            return thread;
        }
    }
}

