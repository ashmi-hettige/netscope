import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientHandler implements Runnable {
    private Socket socket;
    private static final int DEFAULT_POOL = 200;
    private static final int DEFAULT_TIMEOUT = 300;

    // scans running right now, so a CANCEL request can find and stop one
    private static final ConcurrentHashMap<String, ExecutorService> running = new ConcurrentHashMap<>();
    private static final AtomicInteger scanCounter = new AtomicInteger(0);

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

            // a second client can connect just to cancel a scan that is already running
            if (parts[0].equals("CANCEL")) {
                handleCancel(parts, out);
                socket.close();
                return;
            }

            // SCAN <ip> <start> <end> <udpPort> [timeout] [threads]
            if (!parts[0].equals("SCAN") || (parts.length != 5 && parts.length != 7)) {
                out.println("ERROR Invalid request format");
                socket.close();
                return;
            }

            String target = parts[1];
            int start, end, udpPort;
            int timeout = DEFAULT_TIMEOUT;
            int poolSize = DEFAULT_POOL;
            try {
                start = Integer.parseInt(parts[2]);
                end = Integer.parseInt(parts[3]);
                udpPort = Integer.parseInt(parts[4]);
                if (parts.length == 7) {          // the optional settings
                    timeout = Integer.parseInt(parts[5]);
                    poolSize = Integer.parseInt(parts[6]);
                }
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
            if (timeout < 50 || timeout > 5000) {
                out.println("ERROR Timeout must be between 50 and 5000 ms");
                socket.close();
                return;
            }
            if (poolSize < 1 || poolSize > 500) {
                out.println("ERROR Threads must be between 1 and 500");
                socket.close();
                return;
            }

            // tell the client the id first, so it can cancel this scan later
            String id = "S" + scanCounter.incrementAndGet();
            out.println("SCANID " + id);

            scanPorts(id, target, start, end, socket.getInetAddress(), udpPort, timeout, poolSize, out);

            socket.close();
        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage());
        }
    }

    private void handleCancel(String[] parts, PrintWriter out) {
        if (parts.length != 2) {
            out.println("ERROR Usage: CANCEL <scanId>");
            return;
        }
        ExecutorService pool = running.get(parts[1]);
        if (pool == null) {
            out.println("ERROR No running scan with id " + parts[1]);
            return;
        }
        // drops the probes still queued and interrupts the ones in progress
        pool.shutdownNow();
        out.println("CANCELLED " + parts[1]);
        System.out.println("Scan " + parts[1] + " cancelled by request");
    }

    private void scanPorts(String id, String target, int start, int end,
                           InetAddress clientAddr, int udpPort,
                           int timeout, int poolSize, PrintWriter out) {

        int totalPorts = end - start + 1;
        AtomicInteger done = new AtomicInteger(0);
        List<PortResult> openPorts = Collections.synchronizedList(new ArrayList<PortResult>());

        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        running.put(id, pool);
        long startTime = System.currentTimeMillis();

        DatagramSocket udp = null;
        try {
            udp = new DatagramSocket();
        } catch (SocketException e) {
            System.out.println("Could not open UDP socket: " + e.getMessage());
        }
        final DatagramSocket udpSocket = udp;
        final int probeTimeout = timeout;

        try {
            for (int port = start; port <= end; port++) {
                final int p = port;
                pool.submit(() -> {
                    scanOnePort(target, p, probeTimeout, openPorts);
                    int finished = done.incrementAndGet();
                    if (udpSocket != null && udpPort > 0
                            && (finished % 20 == 0 || finished == totalPorts)) {
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
        } finally {
            running.remove(id);
            if (udpSocket != null) udpSocket.close();
        }

        long time = System.currentTimeMillis() - startTime;
        // if we stopped early, some ports never got checked
        boolean cancelled = done.get() < totalPorts;

        openPorts.sort(Comparator.comparingInt(r -> r.port));

        out.println("REPORT_START");
        out.println("Scan ID: " + id);
        out.println("Status: " + (cancelled ? "CANCELLED - partial results" : "complete"));
        out.println("Target: " + target);
        out.println("Range: " + start + "-" + end + " (" + totalPorts + " ports)");
        out.println("Scanned: " + done.get() + " of " + totalPorts);
        out.println("Settings: timeout " + timeout + " ms, " + poolSize + " threads");
        out.println("Open ports: " + openPorts.size());
        out.println("Time: " + time + " ms");
        out.println("PORT\tSERVICE\t\tBANNER");
        for (PortResult r : openPorts) {
            out.println(r.port + "\t" + r.service + "\t" + r.banner);
        }
        out.println("REPORT_END");

        System.out.println("Scan " + id + ": " + done.get() + "/" + totalPorts
                + " ports, " + openPorts.size() + " open" + (cancelled ? " (cancelled)" : ""));
    }

    private void scanOnePort(String host, int port, int timeout, List<PortResult> openPorts) {
        try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress(host, port), timeout);
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
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < count; i++) {
                    char ch = (char) buffer[i];
                    if (ch >= 32 && ch < 127) sb.append(ch);
                }
                String banner = sb.toString().trim();
                if (banner.length() > 0) return banner;
            }
        } catch (IOException e) {
        }
        return "-";
    }

    private void sendProgress(DatagramSocket udp, InetAddress addr, int udpPort, int percent, int open) {
        try {
            String msg = "PROGRESS " + percent + " " + open;
            byte[] data = msg.getBytes();
            udp.send(new DatagramPacket(data, data.length, addr, udpPort));
        } catch (IOException e) {
        }
    }
}