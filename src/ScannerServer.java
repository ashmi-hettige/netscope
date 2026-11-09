import java.io.*;
import java.net.*;

public class ScannerServer {
    static final int PORT = 5000;

    public static void main(String[] args) {
        System.out.println("NetScope server started on port " + PORT);
        System.out.println("(Only scan hosts you are allowed to scan)");

        // try-with-resources: the server socket closes automatically when we exit
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            while (true) {
                Socket client = serverSocket.accept();   // waits until a client connects
                System.out.println("Client connected: " + client.getInetAddress());

                Thread t = new Thread(new ClientHandler(client));
                t.start();
            }
        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }
}