import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

// The report the server sent out to a file, either as plain text or
// as CSV for a spreadsheet.
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

    // the report is a block of summary lines and then a tab separated table.

    public static String toCsv(String report) {
        StringBuilder head = new StringBuilder();
        StringBuilder rows = new StringBuilder();

        head.append("# NetScope scan report\n");
        head.append("# Saved: ").append(timestamp()).append("\n");

        boolean inTable = false;
        String[] columns = { "Port", "Service", "Banner" };   // used if no heading is found

        String[] lines = report.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (line.equals("REPORT_START")) continue;
            if (line.equals("REPORT_END")) break;

            if (line.startsWith("PORT")) {
                // read the column names out of the report instead of hard
                // coding them. a TCP report is PORT SERVICE BANNER but a UDP
                // report also has STATE
                columns = splitCells(line);
                inTable = true;
                continue;
            }

            if (!inTable) {
                head.append("# ").append(line).append("\n");
            } else if (line.startsWith("(")) {
                // a note the server adds instead of listing too many rows
                head.append("# ").append(line).append("\n");
            } else {
                String[] cells = splitCells(line);
                for (int c = 0; c < columns.length; c++) {
                    if (c > 0) rows.append(",");
                    rows.append(csvCell(c < cells.length ? cells[c] : ""));
                }
                rows.append("\n");
            }
        }

        for (int c = 0; c < columns.length; c++) {
            if (c > 0) head.append(",");
            head.append(csvCell(tidy(columns[c])));
        }
        head.append("\n");
        return head.toString() + rows.toString();
    }

    // cells are separated by tabs, but the heading line uses two tabs in
    // places to line things up on screen, which leaves empty pieces. 
    private static String[] splitCells(String line) {
        String[] raw = line.split("\t");
        java.util.List<String> cells = new java.util.ArrayList<String>();
        for (int i = 0; i < raw.length; i++) {
            if (!raw[i].trim().isEmpty()) cells.add(raw[i].trim());
        }
        return cells.toArray(new String[0]);
    }

    /** PORT becomes Port, so the spreadsheet heading is not shouting */
    private static String tidy(String name) {
        if (name.length() < 2) return name;
        return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
    }

    // a banner can easily have a comma in it, something like
    // "Apache/2.4.52, (Ubuntu)", and that would split into an extra column.
   private static String csvCell(String value) {
        String v = value.trim();
        if (v.indexOf(',') < 0 && v.indexOf('"') < 0 && v.indexOf('\n') < 0) {
            return v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    // suggest a file name so nobody has to invent one. the target and the
    // time go in it, which also stops two saves overwriting each other
    public static String suggestFileName(String target, String extension) {
        String safeTarget = target.replaceAll("[^A-Za-z0-9._-]", "_");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date());
        return "netscope_" + safeTarget + "_" + stamp + extension;
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }
}