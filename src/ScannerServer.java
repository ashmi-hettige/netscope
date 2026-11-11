import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ScannerServer {
    static final int PORT = 5000;
    static AtomicInteger clientCount = new AtomicInteger(0);

    public static void main(String[] args) {
        System.out.println("NetScope server started on port " + PORT);
        System.out.println("(Only scan hosts you are allowed to scan)");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket client = serverSocket.accept();
                int id = clientCount.incrementAndGet();

                // give each client its own named thread so we can see them run together
                Thread t = new Thread(new ClientHandler(client), "client-" + id);
                System.out.println("Client #" + id + " connected from "
                        + client.getInetAddress() + "  ->  thread " + t.getName());
                t.start();
            }
        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }
}