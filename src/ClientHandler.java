import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientHandler implements Runnable {
    private Socket socket;
    private static final int DEFAULT_POOL = 200;
    private static final int DEFAULT_TIMEOUT = 300;
    private static final int DEFAULT_UDP_POOL = 50;
    private static final int DEFAULT_UDP_TIMEOUT = 700;

    // every scan that is still going, keyed by its id. a CANCEL request
    // arrives on a different connection and needs to find the right pool
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

            if (parts[0].equals("CANCEL")) {
                handleCancel(parts, out);
            } else if (parts[0].equals("SCAN")) {
                handleTcpScan(parts, out);
            } else if (parts[0].equals("UDPSCAN")) {
                handleUdpScan(parts, out);
            } else if (parts[0].equals("UDPCOMMON")) {
                handleUdpCommon(parts, out);
            } else {
                out.println("ERROR Unknown request " + parts[0]);
            }

            socket.close();
        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage());
        }
    }

    // ---------------- TCP ----------------

    // SCAN <ip> <start> <end> <udpPort> [timeout] [threads]
    private void handleTcpScan(String[] parts, PrintWriter out) {
        if (parts.length != 5 && parts.length != 7) {
            out.println("ERROR Invalid request format");
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
            if (parts.length == 7) {
                timeout = Integer.parseInt(parts[5]);
                poolSize = Integer.parseInt(parts[6]);
            }
        } catch (NumberFormatException e) {
            out.println("ERROR Ports must be numbers");
            return;
        }
        if (!checkRange(start, end, out) || !checkSettings(timeout, poolSize, out)) return;

        int[] ports = new int[end - start + 1];
        for (int i = 0; i < ports.length; i++) ports[i] = start + i;

        String id = newScanId(out);
        scan("TCP", id, target, ports, start + "-" + end,
             socket.getInetAddress(), udpPort, timeout, poolSize, out);
    }

    // ---------------- UDP ----------------

    // UDPSCAN <ip> <start> <end> <udpPort> [timeout] [threads]
    private void handleUdpScan(String[] parts, PrintWriter out) {
        if (parts.length != 5 && parts.length != 7) {
            out.println("ERROR Invalid request format");
            return;
        }
        String target = parts[1];
        int start, end, udpPort;
        int timeout = DEFAULT_UDP_TIMEOUT;
        int poolSize = DEFAULT_UDP_POOL;
        try {
            start = Integer.parseInt(parts[2]);
            end = Integer.parseInt(parts[3]);
            udpPort = Integer.parseInt(parts[4]);
            if (parts.length == 7) {
                timeout = Integer.parseInt(parts[5]);
                poolSize = Integer.parseInt(parts[6]);
            }
        } catch (NumberFormatException e) {
            out.println("ERROR Ports must be numbers");
            return;
        }
        if (!checkRange(start, end, out) || !checkSettings(timeout, poolSize, out)) return;

        int[] ports = new int[end - start + 1];
        for (int i = 0; i < ports.length; i++) ports[i] = start + i;

        String id = newScanId(out);
        scan("UDP", id, target, ports, start + "-" + end,
             socket.getInetAddress(), udpPort, timeout, poolSize, out);
    }

    // UDPCOMMON <ip> <udpPort> [timeout] [threads]
    private void handleUdpCommon(String[] parts, PrintWriter out) {
        if (parts.length != 3 && parts.length != 5) {
            out.println("ERROR Invalid request format");
            return;
        }
        String target = parts[1];
        int udpPort;
        int timeout = DEFAULT_UDP_TIMEOUT;
        int poolSize = DEFAULT_UDP_POOL;
        try {
            udpPort = Integer.parseInt(parts[2]);
            if (parts.length == 5) {
                timeout = Integer.parseInt(parts[3]);
                poolSize = Integer.parseInt(parts[4]);
            }
        } catch (NumberFormatException e) {
            out.println("ERROR Port must be a number");
            return;
        }
        if (!checkSettings(timeout, poolSize, out)) return;

        int[] ports = UdpServiceIdentifier.commonPorts();
        String id = newScanId(out);
        scan("UDP", id, target, ports, "common UDP services",
             socket.getInetAddress(), udpPort, timeout, poolSize, out);
    }

    // ---------------- shared ----------------

    private boolean checkRange(int start, int end, PrintWriter out) {
        if (start < 1 || end > 65535 || start > end) {
            out.println("ERROR Port range must be between 1 and 65535, and start <= end");
            return false;
        }
        return true;
    }

    private boolean checkSettings(int timeout, int poolSize, PrintWriter out) {
        if (timeout < 50 || timeout > 5000) {
            out.println("ERROR Timeout must be between 50 and 5000 ms");
            return false;
        }
        if (poolSize < 1 || poolSize > 500) {
            out.println("ERROR Threads must be between 1 and 500");
            return false;
        }
        return true;
    }

    private String newScanId(PrintWriter out) {
        String id = "S" + scanCounter.incrementAndGet();
        out.println("SCANID " + id);
        return id;
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
        pool.shutdownNow();
        out.println("CANCELLED " + parts[1]);
        System.out.println("Scan " + parts[1] + " cancelled by request");
    }

    // one scan loop for both protocols. the only thing that differs is how a
    // single port gets probed, 
    private void scan(String protocol, String id, String target, int[] ports, String rangeText,
                      InetAddress clientAddr, int udpPort,
                      int timeout, int poolSize, PrintWriter out) {

        int totalPorts = ports.length;
        AtomicInteger done = new AtomicInteger(0);
        List<PortResult> found = Collections.synchronizedList(new ArrayList<PortResult>());
        AtomicInteger closedCount = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        running.put(id, pool);
        long startTime = System.currentTimeMillis();

        DatagramSocket progressSocket = null;
        try {
            progressSocket = new DatagramSocket();
        } catch (SocketException e) {
            System.out.println("Could not open UDP socket: " + e.getMessage());
        }
        final DatagramSocket udpSocket = progressSocket;
        final boolean isUdp = protocol.equals("UDP");

        try {
            for (int i = 0; i < ports.length; i++) {
                final int p = ports[i];
                pool.submit(() -> {
                    if (isUdp) probeUdpPort(target, p, timeout, found, closedCount);
                    else scanOnePort(target, p, timeout, found);

                    int finished = done.incrementAndGet();
                    if (udpSocket != null && udpPort > 0
                            && (finished % 20 == 0 || finished == totalPorts)) {
                        int percent = finished * 100 / totalPorts;
                        sendProgress(udpSocket, clientAddr, udpPort, percent, found.size());
                    }
                });
            }
            pool.shutdown();
            try {

                int stepsWaited = 0;
                while (!pool.awaitTermination(10, TimeUnit.SECONDS) && stepsWaited < 180) {
                    stepsWaited++;
                }
            } catch (InterruptedException e) {
                System.out.println("Scan interrupted");
            }
        } finally {
            running.remove(id);
            if (udpSocket != null) udpSocket.close();
        }

        long time = System.currentTimeMillis() - startTime;
        boolean cancelled = done.get() < totalPorts;

        // take a copy before building the report. if the wait above ever runs
        // out while a worker is still adding, looping over the live list throws
        // ConcurrentModificationException and kills this client's thread. the
        // ArrayList constructor calls toArray(), which locks, so this copy is safe
        List<PortResult> results = new ArrayList<>(found);
        results.sort(Comparator.comparingInt(r -> r.port));

        out.println("REPORT_START");
        out.println("Scan ID: " + id);
        // only UDP reports carry this line. the TCP report keeps exactly the
        // shape written down in PROTOCOL.md section 3
        if (isUdp) out.println("Protocol: UDP");
        out.println("Status: " + (cancelled ? "CANCELLED - partial results" : "complete"));
        out.println("Target: " + target);
        out.println("Range: " + rangeText + " (" + totalPorts + " ports)");
        out.println("Scanned: " + done.get() + " of " + totalPorts);
        out.println("Settings: timeout " + timeout + " ms, " + poolSize + " threads");

        if (isUdp) {
            int answered = 0, silent = 0;
            for (PortResult r : results) {
                if (r.state.equals(UdpProbe.OPEN)) answered++; else silent++;
            }
            out.println("Replied (open): " + answered);
            out.println("No answer (open|filtered): " + silent);
            out.println("Reported closed: " + closedCount.get());
            out.println("Time: " + time + " ms");
            out.println("PORT\tSTATE\t\tSERVICE\t\tINFO");
            for (PortResult r : results) {
                if (r.state.equals(UdpProbe.OPEN)) {
                    out.println(r.port + "\t" + r.state + "\t\t" + r.service + "\t\t" + r.banner);
                }
            }
            // a firewalled host says nothing to anything, so listing thousands
            // of silent ports would bury the lines that matter. past 50 we just
            // print the count
            if (silent > 0 && silent <= 50) {
                for (PortResult r : results) {
                    if (!r.state.equals(UdpProbe.OPEN)) {
                        out.println(r.port + "\t" + r.state + "\t" + r.service + "\t\t-");
                    }
                }
            } else if (silent > 50) {
                out.println("(" + silent + " ports gave no answer, too many to list)");
            }
        } else {
            out.println("Open ports: " + results.size());
            out.println("Time: " + time + " ms");
            out.println("PORT\tSERVICE\t\tBANNER");
            for (PortResult r : results) {
                out.println(r.port + "\t" + r.service + "\t" + r.banner);
            }
        }
        out.println("REPORT_END");

        System.out.println("Scan " + id + " (" + protocol + "): " + done.get() + "/" + totalPorts
                + " ports" + (cancelled ? " (cancelled)" : ""));
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

    // a UDP port that told us it was closed is not worth a row in the report
    private void probeUdpPort(String host, int port, int timeout,
                              List<PortResult> found, AtomicInteger closedCount) {
        UdpProbe.Result r = UdpProbe.probe(host, port, timeout);
        if (r.state.equals(UdpProbe.CLOSED)) {
            closedCount.incrementAndGet();
            return;
        }
        found.add(new PortResult(port, UdpServiceIdentifier.identify(port), r.info, r.state));
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