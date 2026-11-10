import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientHandler implements Runnable {
    private Socket socket;
    private static final int POOL_SIZE = 200;
    private static final int TIMEOUT = 300;   // ms to wait for a port (used by M2's probe)

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            String request = in.readLine();
            System.out.println("Got request: " + request);

            // expected format: SCAN <ip> <start> <end> <udpPort>
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

        AtomicInteger done = new AtomicInteger(0);
        List<PortResult> openPorts = Collections.synchronizedList(new ArrayList<PortResult>());

        // a pool of threads so many ports are checked at the same time
        ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE);
        long startTime = System.currentTimeMillis();

        for (int port = start; port <= end; port++) {
            final int p = port;
            pool.submit(() -> {
                scanOnePort(target, p, openPorts);
                done.incrementAndGet();
            });
        }

        pool.shutdown();
        try {
            pool.awaitTermination(1, TimeUnit.MINUTES);   // wait for every task to finish
        } catch (InterruptedException e) {
            System.out.println("Scan interrupted");
        }

        long time = System.currentTimeMillis() - startTime;

        // send the report back to the client
        out.println("REPORT_START");
        out.println("Target: " + target);
        out.println("Range: " + start + "-" + end);
        out.println("Open ports: " + openPorts.size());
        out.println("Time: " + time + " ms");
        for (PortResult r : openPorts) {
            out.println(r.port + "\t" + r.service + "\t" + r.banner);
        }
        out.println("REPORT_END");

        System.out.println("Scanned " + done.get() + " ports, " + openPorts.size() + " open");
    }

    // Stub for now - Member 2 fills in the real port check.
    // clientAddr and udpPort get used when M2 adds the UDP progress part.
    private void scanOnePort(String host, int port, List<PortResult> openPorts) {
        // TODO (M2): try to connect with a timeout; if it connects, the port is open
    }
}