import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ReportWriter {

    public static void writeText(String path, String report) throws IOException {
        PrintWriter out = new PrintWriter(new FileWriter(path));
        try {
            out.println("NetScope scan report");
            out.println("Saved: " + timestamp());
            out.println();
            out.print(report);
        } finally {
            out.close();
        }
    }

    public static void writeCsv(String path, String report) throws IOException {
        PrintWriter out = new PrintWriter(new FileWriter(path));
        try {
            out.print(toCsv(report));
        } finally {
            out.close();
        }
    }

    public static String toCsv(String report) {
        StringBuilder head = new StringBuilder();
        StringBuilder rows = new StringBuilder();

        head.append("# NetScope scan report\n");
        head.append("# Saved: ").append(timestamp()).append("\n");

        boolean inTable = false;
        String[] lines = report.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (line.equals("REPORT_START")) continue;
            if (line.equals("REPORT_END")) break;

            if (line.startsWith("PORT")) {        // the table's own heading
                inTable = true;
                continue;
            }

            if (!inTable) {
                head.append("# ").append(line).append("\n");
            } else {
                String[] cells = line.split("\t");
                rows.append(csvCell(cells.length > 0 ? cells[0] : ""));
                rows.append(",").append(csvCell(cells.length > 1 ? cells[1] : ""));
                rows.append(",").append(csvCell(cells.length > 2 ? cells[2] : ""));
                rows.append("\n");
            }
        }

        head.append("Port,Service,Banner\n");
        return head.toString() + rows.toString();
    }

    private static String csvCell(String value) {
        String v = value.trim();
        if (v.indexOf(',') < 0 && v.indexOf('"') < 0 && v.indexOf('\n') < 0) {
            return v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    /** a file name nobody has to think about, with the target and the time in it */
    public static String suggestFileName(String target, String extension) {
        String safeTarget = target.replaceAll("[^A-Za-z0-9._-]", "_");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date());
        return "netscope_" + safeTarget + "_" + stamp + extension;
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }
}
