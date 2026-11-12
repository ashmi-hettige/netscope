import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
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
    private JButton saveButton = new JButton("Save report");
    private JProgressBar progressBar = new JProgressBar(0, 100);
    private JTextArea reportArea = new JTextArea(15, 52);
    private JLabel statusLabel = new JLabel("Ready. Scan only hosts you are allowed to scan.");

    private volatile String currentScanId = null;
    // remembered so the suggested file name can mention what was scanned
    private volatile String lastTarget = "scan";

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
        row2.add(saveButton);

        JPanel top = new JPanel(new GridLayout(2, 1));
        top.add(row1);
        top.add(row2);

        stopButton.setEnabled(false);
        saveButton.setEnabled(false);      // nothing worth saving until a scan finishes
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
        saveButton.addActionListener(e -> saveReport());

        pack();
        setLocationRelativeTo(null);
    }

    // ---------------- saving ----------------

    private void saveReport() {
        String report = reportArea.getText();
        if (report.trim().isEmpty()) {
            statusLabel.setText("There is no report to save yet");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save scan report");
        FileNameExtensionFilter textFilter = new FileNameExtensionFilter("Text file (*.txt)", "txt");
        final FileNameExtensionFilter csvFilter = new FileNameExtensionFilter("Spreadsheet (*.csv)", "csv");
        chooser.addChoosableFileFilter(textFilter);
        chooser.addChoosableFileFilter(csvFilter);
        chooser.setFileFilter(textFilter);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setSelectedFile(new File(ReportWriter.suggestFileName(lastTarget, ".txt")));

        chooser.addPropertyChangeListener(
            JFileChooser.FILE_FILTER_CHANGED_PROPERTY,
            event -> {
                String ext = (chooser.getFileFilter() == csvFilter) ? ".csv" : ".txt";
                chooser.setSelectedFile(new File(ReportWriter.suggestFileName(lastTarget, ext)));
            });

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        boolean wantCsv = chooser.getFileFilter() == csvFilter;
        String path = chooser.getSelectedFile().getAbsolutePath();

        // if the person picked CSV from the dropdown but the name still ends
        
        String lower = path.toLowerCase();
        if (lower.endsWith(".csv")) wantCsv = true;
        else if (lower.endsWith(".txt")) wantCsv = false;
        else path = path + (wantCsv ? ".csv" : ".txt");

        File target = new File(path);
        if (target.exists()) {
            int answer = JOptionPane.showConfirmDialog(this,
                    target.getName() + " already exists. Replace it?",
                    "Replace file", JOptionPane.YES_NO_OPTION);
            if (answer != JOptionPane.YES_OPTION) return;
        }

        // writing to disk is not the event thread's job, so it goes on its own
        final String finalPath = path;
        final boolean csv = wantCsv;
        saveButton.setEnabled(false);
        statusLabel.setText("Saving...");
        new Thread(() -> {
            String message;
            try {
                if (csv) ReportWriter.writeCsv(finalPath, report);
                else ReportWriter.writeText(finalPath, report);
                message = "Saved to " + finalPath;
            } catch (IOException e) {
                message = "Could not save: " + e.getMessage();
            }
            final String m = message;
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText(m);
                saveButton.setEnabled(true);
            });
        }).start();
    }

    // ---------------- scanning ----------------

    private void startScan() {
        scanButton.setEnabled(false);
        saveButton.setEnabled(false);
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
        lastTarget = target;

        new Thread(() -> runScan(host, target, s, e, t, n)).start();
    }

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
            // a report only becomes savable once there is something in the box
            saveButton.setEnabled(!reportArea.getText().trim().isEmpty());
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

            String first = in.readLine();
            if (first == null) {
                finished("Server closed the connection");
                return;
            }
            if (first.startsWith("SCANID")) {
                currentScanId = first.split(" ")[1];
                SwingUtilities.invokeLater(() -> stopButton.setEnabled(true));
            } else {
                final String msg = first;
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
