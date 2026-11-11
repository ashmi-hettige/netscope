import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// Measures how long the same scan takes with different thread pool sizes.
public class Benchmark {
    static final int TIMEOUT = 300;

    public static void main(String[] args) throws InterruptedException {
        String host = "127.0.0.1";
        int start = 1;
        int end = 5100;
        if (args.length == 3) {
            host = args[0];
            start = Integer.parseInt(args[1]);
            end = Integer.parseInt(args[2]);
        }

        int[] poolSizes = {1, 10, 50, 200};

        System.out.println("Benchmark: scanning " + host + " ports " + start + "-" + end);
        System.out.println("Threads\tTime (ms)\tOpen\tSpeedup");

        long baseTime = 0;
        for (int size : poolSizes) {
            AtomicInteger open = new AtomicInteger(0);
            long time = runScan(host, start, end, size, open);
            if (size == 1) {
                baseTime = time;   // 1 thread = the sequential scan we compare against
            }
            double speedup = (double) baseTime / time;
            System.out.printf("%d\t%d\t\t%d\t%.1fx%n", size, time, open.get(), speedup);
        }
    }

    // same idea as the server: one task per port, wait for all of them, return the time taken
    static long runScan(String host, int start, int end, int poolSize, AtomicInteger open)
            throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        long startTime = System.currentTimeMillis();

        for (int port = start; port <= end; port++) {
            final int p = port;
            pool.submit(() -> {
                try {
                    Socket s = new Socket();
                    s.connect(new InetSocketAddress(host, p), TIMEOUT);
                    open.incrementAndGet();
                    s.close();
                } catch (IOException e) {
                    // closed port
                }
            });
        }

        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.MINUTES);
        return System.currentTimeMillis() - startTime;
    }
}