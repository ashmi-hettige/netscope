import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientHandler implements Runnable {
    private Socket socket;
    private static final int POOL_SIZE = 200;
    private static final int TIMEOUT = 300;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            String request = in.readLine();
            if (request == null) { socket.close(); return; }
            System.out.println("Got request: " + request);

            String[] parts = request.split(" ");
            if (parts.length != 5 || !parts[0].equals("SCAN")) {
                out.println("ERROR Invalid request format");
                socket.close();
                return;
            }

            String target = parts[1];
            int start, end, udpPort;
            try {
                start = Integer.parseInt(parts[2]);
                end = Integer.parseInt(parts[3]);
                udpPort = Integer.parseInt(parts[4]);
            } catch (NumberFormatException e) {
                out.println("ERROR Ports must be numbers");
                socket.close();
                return;
            }

            if (start < 1 || end > 65535 || start > end) {
                out.println("ERROR Port range must be between 1 and 65535, and start <= end");
                socket.close();
                return;
            }

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

    private void scanOnePort(String host, int port, List<PortResult> openPorts) {
        try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress(host, port), TIMEOUT);
            String banner = grabBanner(s);
            String service = ServiceIdentifier.identify(port);
            openPorts.add(new PortResult(port, service, banner));
            s.close();
        } catch (IOException e) {
        }
    }

    private String grabBanner(Socket s) {
        try {
            s.setSoTimeout(200);
            InputStream input = s.getInputStream();
            byte[] buffer = new byte[100];
            int count = input.read(buffer);
            if (count > 0) {
                // keep only normal readable characters, drop the binary bytes
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < count; i++) {
                    char ch = (char) buffer[i];
                    if (ch >= 32 && ch < 127) {
                        sb.append(ch);
                    }
                }
                String banner = sb.toString().trim();
                if (banner.length() > 0) {
                    return banner;
                }
            }
        } catch (IOException e) {
        }
        return "-";
    }

    private void sendProgress(DatagramSocket udp, InetAddress addr, int udpPort, int percent, int open) {
        try {
            String msg = "PROGRESS " + percent + " " + open;
            byte[] data = msg.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, addr, udpPort);
            udp.send(packet);
        } catch (IOException e) {
        }
    }
}