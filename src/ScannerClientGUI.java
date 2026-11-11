import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;

public class ScannerClientGUI extends JFrame {

    private JTextField hostField = new JTextField("localhost", 10);
    private JTextField targetField = new JTextField("127.0.0.1", 10);
    private JTextField startField = new JTextField("1", 5);
    private JTextField endField = new JTextField("1000", 5);
    private JButton scanButton = new JButton("Scan");
    private JProgressBar progressBar = new JProgressBar(0, 100);
    private JTextArea reportArea = new JTextArea(15, 45);
    private JLabel statusLabel = new JLabel("Ready. Scan only hosts you are allowed to scan.");

    public ScannerClientGUI() {
        setTitle("NetScope Port Scanner");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel top = new JPanel();
        top.add(new JLabel("Server:")); top.add(hostField);
        top.add(new JLabel("Target:")); top.add(targetField);
        top.add(new JLabel("Ports:")); top.add(startField);
        top.add(new JLabel("to")); top.add(endField);
        top.add(scanButton);

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

        pack();
        setLocationRelativeTo(null);
    }

    private void startScan() {
        scanButton.setEnabled(false);
        reportArea.setText("");
        progressBar.setValue(0);
        statusLabel.setText("Scanning...");

        // read the fields now (on the UI thread), then do the network work on another thread
        String host = hostField.getText().trim();
        String target = targetField.getText().trim();
        String startText = startField.getText().trim();
        String endText = endField.getText().trim();

        new Thread(() -> runScan(host, target, startText, endText)).start();
    }

    private void runScan(String host, String target, String startText, String endText) {
        int start, end;
        try {
            start = Integer.parseInt(startText);
            end = Integer.parseInt(endText);
        } catch (NumberFormatException e) {
            showStatus("Ports must be numbers");
            SwingUtilities.invokeLater(() -> scanButton.setEnabled(true));
            return;
        }

        DatagramSocket udpSocket = null;
        try {
            udpSocket = new DatagramSocket();
            int udpPort = udpSocket.getLocalPort();

            // listen for progress updates on its own thread
            final DatagramSocket udp = udpSocket;
            Thread progressThread = new Thread(() -> listenProgress(udp));
            progressThread.setDaemon(true);
            progressThread.start();

            Socket socket = new Socket(host, 5000);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println("SCAN " + target + " " + start + " " + end + " " + udpPort);

            StringBuilder report = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                report.append(line).append("\n");
            }
            final String text = report.toString();
            SwingUtilities.invokeLater(() -> {
                reportArea.setText(text);
                progressBar.setValue(100);
                statusLabel.setText("Done");
                scanButton.setEnabled(true);
            });

            socket.close();
        } catch (IOException e) {
            showStatus("Could not connect: " + e.getMessage());
            SwingUtilities.invokeLater(() -> scanButton.setEnabled(true));
        } finally {
            if (udpSocket != null) udpSocket.close();
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
            // socket closed when the scan ends, stop quietly
        }
    }

    private void showStatus(String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(text));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ScannerClientGUI().setVisible(true));
    }
}
