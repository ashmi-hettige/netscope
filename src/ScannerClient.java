import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ScannerClient {
    public static void main(String[] args) {
        String host = "localhost";
        int port = 5000;

        // ask the user what to scan
        Scanner sc = new Scanner(System.in);
        System.out.print("Target IP to scan: ");
        String target = sc.nextLine().trim();
        System.out.print("Start port: ");
        int start = Integer.parseInt(sc.nextLine().trim());
        System.out.print("End port: ");
        int end = Integer.parseInt(sc.nextLine().trim());

        try {
            Socket socket = new Socket(host, port);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));

            // send the request. last value is the UDP port - we add real UDP later, so 0 for now
            out.println("SCAN " + target + " " + start + " " + end + " 0");

            // print everything the server sends back, until it closes
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println(line);
            }

            socket.close();
        } catch (IOException e) {
            System.out.println("Could not connect to server: " + e.getMessage());
        }
    }
}
