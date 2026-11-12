import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;

public class ScannerClientGUI extends JFrame {

    private JTextField hostField = new JTextField("localhost", 9);
    private JTextField targetField = new JTextField("127.0.0.1", 11);
    private JTextField startField = new JTextField("1", 6);
    private JTextField endField = new JTextField("1000", 6);
    private JTextField timeoutField = new JTextField("300", 5);
    private JTextField threadsField = new JTextField("200", 5);
    private JButton scanButton = new JButton("Scan");
    private JButton stopButton = new JButton("Stop");
    private JProgressBar progressBar = new JProgressBar(0, 100);
    private JTextArea reportArea = new JTextArea(15, 52);
    private JLabel statusLabel = new JLabel("Ready. Scan only hosts you are allowed to scan.");

    // the id the server gives this scan, so the Stop button can cancel it
    private volatile String currentScanId = null;

    public ScannerClientGUI() {
        setTitle("NetScope Port Scanner");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.add(new JLabel("Server:"));  row1.add(hostField);
        row1.add(new JLabel("Target:"));  row1.add(targetField);
        row1.add(new JLabel("Ports:"));   row1.add(startField);
        row1.add(new JLabel("to"));       row1.add(endField);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.add(new JLabel("Timeout (ms):")); row2.add(timeoutField);
        row2.add(new JLabel("Threads:"));      row2.add(threadsField);
        row2.add(scanButton);
        row2.add(stopButton);

        JPanel top = new JPanel(new GridLayout(2, 1));
        top.add(row1);
        top.add(row2);

        stopButton.setEnabled(false);
        progressBar.setStringPainted(true);
        reportArea.setEditable(false);
        reportArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(progressBar, BorderLayout.NORTH);
        bottom.add(statusLabel, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(reportArea), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        scanButton.addActionListener(e -> startScan());
        stopButton.addActionListener(e -> cancelScan());

        pack();
        setLocationRelativeTo(null);
    }

    private void startScan() {
        scanButton.setEnabled(false);
        reportArea.setText("");
        progressBar.setValue(0);
        statusLabel.setText("Scanning...");
        currentScanId = null;

        String host = hostField.getText().trim();
        String target = targetField.getText().trim();
        String s = startField.getText().trim();
        String e = endField.getText().trim();
        String t = timeoutField.getText().trim();
        String n = threadsField.getText().trim();

        new Thread(() -> runScan(host, target, s, e, t, n)).start();
    }

    // open a second connection just to tell the server to stop this scan
    private void cancelScan() {
        final String id = currentScanId;
        if (id == null) return;
        stopButton.setEnabled(false);
        final String host = hostField.getText().trim();
        new Thread(() -> {
            try {
                Socket s = new Socket(host, 5000);
                PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                out.println("CANCEL " + id);
                String reply = in.readLine();
                s.close();
                final String r = (reply == null) ? "no reply" : reply;
                SwingUtilities.invokeLater(() -> statusLabel.setText("Stop requested: " + r));
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> statusLabel.setText("Stop failed: " + ex.getMessage()));
            }
        }).start();
    }

    private void finished(String message) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(message);
            scanButton.setEnabled(true);
            stopButton.setEnabled(false);
        });
    }

    private void runScan(String host, String target, String startText, String endText,
                         String timeoutText, String threadsText) {
        int start, end, timeout, threads;
        try {
            start = Integer.parseInt(startText);
            end = Integer.parseInt(endText);
            timeout = Integer.parseInt(timeoutText);
            threads = Integer.parseInt(threadsText);
        } catch (NumberFormatException e) {
            finished("Ports, timeout and threads must be numbers");
            return;
        }

        DatagramSocket udpSocket = null;
        Socket socket = null;
        try {
            udpSocket = new DatagramSocket();
            int udpPort = udpSocket.getLocalPort();

            final DatagramSocket udp = udpSocket;
            Thread progressThread = new Thread(() -> listenProgress(udp));
            progressThread.setDaemon(true);
            progressThread.start();

            socket = new Socket(host, 5000);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println("SCAN " + target + " " + start + " " + end + " " + udpPort
                    + " " + timeout + " " + threads);

            // the server sends the scan id first, then the report
            String first = in.readLine();
            if (first == null) {
                finished("Server closed the connection");
                return;
            }
            if (first.startsWith("SCANID")) {
                currentScanId = first.split(" ")[1];
                SwingUtilities.invokeLater(() -> stopButton.setEnabled(true));
            } else {
                final String msg = first;   // an ERROR line
                SwingUtilities.invokeLater(() -> reportArea.setText(msg));
                finished(msg);
                return;
            }

            StringBuilder report = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                report.append(line).append("\n");
            }
            final String text = report.toString();
            SwingUtilities.invokeLater(() -> {
                reportArea.setText(text);
                progressBar.setValue(100);
            });
            finished("Done");

        } catch (IOException e) {
            finished("Could not connect: " + e.getMessage());
        } finally {
            if (udpSocket != null) udpSocket.close();
            if (socket != null) { try { socket.close(); } catch (IOException ignored) {} }
        }
    }

    private void listenProgress(DatagramSocket udpSocket) {
        byte[] buffer = new byte[200];
        try {
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength());
                String[] parts = msg.split(" ");
                if (parts.length == 3) {
                    int percent = Integer.parseInt(parts[1]);
                    String open = parts[2];
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(percent);
                        statusLabel.setText("Scanning... open so far: " + open);
                    });
                }
            }
        } catch (IOException e) {
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ScannerClientGUI().setVisible(true));
    }
}