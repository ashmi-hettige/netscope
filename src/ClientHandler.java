import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientHandler implements Runnable {
    private Socket socket;
    private static final int POOL_SIZE = 200;
    private static final int TIMEOUT = 300;   // ms to wait for a port to answer

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            String request = in.readLine();
            System.out.println("Got request: " + request);

            String[] parts = request.split(" ");
            if (parts.length != 5 || !parts[0].equals("SCAN")) {
                out.println("ERROR Invalid request");
                socket.close();
                return;
            }

            String target = parts[1];
            int start = Integer.parseInt(parts[2]);
            int end = Integer.parseInt(parts[3]);
            int udpPort = Integer.parseInt(parts[4]);

            scanPorts(target, start, end, socket.getInetAddress(), udpPort, out);

            socket.close();
        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage());
        }
    }

    private void scanPorts(String target, int start, int end,
                           InetAddress clientAddr, int udpPort, PrintWriter out) {

        int totalPorts = end - start + 1;
        AtomicInteger done = new AtomicInteger(0);
        List<PortResult> openPorts = Collections.synchronizedList(new ArrayList<PortResult>());

        ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE);
        long startTime = System.currentTimeMillis();

        // (M2) open a UDP socket for sending progress updates
        DatagramSocket udp = null;
        try {
            udp = new DatagramSocket();
        } catch (SocketException e) {
            System.out.println("Could not open UDP socket: " + e.getMessage());
        }
        final DatagramSocket udpSocket = udp;

        for (int port = start; port <= end; port++) {
            final int p = port;
            pool.submit(() -> {
                scanOnePort(target, p, openPorts);
                int finished = done.incrementAndGet();
                // (M2) send progress every 20 ports, only if the client gave a UDP port
                if (udpSocket != null && udpPort > 0 && (finished % 20 == 0 || finished == totalPorts)) {
                    int percent = finished * 100 / totalPorts;
                    sendProgress(udpSocket, clientAddr, udpPort, percent, openPorts.size());
                }
            });
        }

        pool.shutdown();
        try {
            pool.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            System.out.println("Scan interrupted");
        }
        if (udpSocket != null) udpSocket.close();

        long time = System.currentTimeMillis() - startTime;

        openPorts.sort(Comparator.comparingInt(r -> r.port));

        out.println("REPORT_START");
        out.println("Target: " + target);
        out.println("Range: " + start + "-" + end + " (" + totalPorts + " ports)");
        out.println("Open ports: " + openPorts.size());
        out.println("Time: " + time + " ms");
        out.println("PORT\tSERVICE\t\tBANNER");
        for (PortResult r : openPorts) {
            out.println(r.port + "\t" + r.service + "\t" + r.banner);
        }
        out.println("REPORT_END");

        System.out.println("Scanned " + done.get() + " ports, " + openPorts.size() + " open");
    }

    // (M2) the real check: try to connect; if it works, the port is open
    private void scanOnePort(String host, int port, List<PortResult> openPorts) {
        try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress(host, port), TIMEOUT);
            String banner = grabBanner(s);
            String service = ServiceIdentifier.identify(port);
            openPorts.add(new PortResult(port, service, banner));
            s.close();
        } catch (IOException e) {
            // couldn't connect -> port is closed, ignore it
        }
    }

    // (M2) read the first bytes some services send on connect
    private String grabBanner(Socket s) {
        try {
            s.setSoTimeout(200);
            InputStream input = s.getInputStream();
            byte[] buffer = new byte[100];
            int count = input.read(buffer);
            if (count > 0) {
                return new String(buffer, 0, count).replace("\n", " ").replace("\r", " ").trim();
            }
        } catch (IOException e) {
            // many services send nothing, that's fine
        }
        return "-";
    }

    // (M2) send one progress update over UDP (connectionless, fire-and-forget)
    private void sendProgress(DatagramSocket udp, InetAddress addr, int udpPort, int percent, int open) {
        try {
            String msg = "PROGRESS " + percent + " " + open;
            byte[] data = msg.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, addr, udpPort);
            udp.send(packet);
        } catch (IOException e) {
            // losing a progress packet is ok -> ignore
        }
    }
}