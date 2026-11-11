import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ScannerClient {
    // shared flag so the progress thread stops printing once the report starts
    private static volatile boolean scanning = true;

    public static void main(String[] args) {
        String host = "localhost";
        int port = 5000;

        Scanner sc = new Scanner(System.in);
        System.out.print("Target IP to scan: ");
        String target = sc.nextLine().trim();
        System.out.print("Start port: ");
        int start = Integer.parseInt(sc.nextLine().trim());
        System.out.print("End port: ");
        int end = Integer.parseInt(sc.nextLine().trim());

        try {
            DatagramSocket udpSocket = new DatagramSocket();
            int udpPort = udpSocket.getLocalPort();

            Thread progressThread = new Thread(() -> listenProgress(udpSocket));
            progressThread.setDaemon(true);
            progressThread.start();

            Socket socket = new Socket(host, port);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println("SCAN " + target + " " + start + " " + end + " " + udpPort);

            String line;
            while ((line = in.readLine()) != null) {
                if (line.equals("REPORT_START")) {
                    scanning = false;       // stop the progress line
                    System.out.println();   // move to a clean line before the report
                }
                System.out.println(line);
            }

            socket.close();
            udpSocket.close();
        } catch (IOException e) {
            System.out.println("Could not connect to server: " + e.getMessage());
        }
    }

    private static void listenProgress(DatagramSocket udpSocket) {
        byte[] buffer = new byte[200];
        try {
            while (scanning) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);
                if (!scanning) break;       // report has started, don't print over it
                String msg = new String(packet.getData(), 0, packet.getLength());
                String[] parts = msg.split(" ");
                if (parts.length == 3) {
                    System.out.print("\rProgress: " + parts[1] + "%  open so far: " + parts[2] + "   ");
                }
            }
        } catch (IOException e) {
            // socket closed when the scan ends, stop quietly
        }
    }
}
