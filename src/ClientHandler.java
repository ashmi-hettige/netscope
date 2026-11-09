import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {
    private Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            String request = in.readLine();
            System.out.println("Got request: " + request);

            // For now we just reply. The real scanning is added in the next days.
            out.println("REPORT_START");
            out.println("Server received: " + request);
            out.println("Scanning not implemented yet");
            out.println("REPORT_END");

            socket.close();
        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage());
        }
    }
}