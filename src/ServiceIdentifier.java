import java.util.HashMap;
import java.util.Map;

// NetScope — maps well-known ports to service names. Owner: Member 2.
public class ServiceIdentifier {
    private static final Map<Integer, String> WELL_KNOWN = new HashMap<>();
    static {
        WELL_KNOWN.put(20, "FTP-data"); WELL_KNOWN.put(21, "FTP");
        WELL_KNOWN.put(22, "SSH");      WELL_KNOWN.put(23, "Telnet");
        WELL_KNOWN.put(25, "SMTP");     WELL_KNOWN.put(53, "DNS");
        WELL_KNOWN.put(80, "HTTP");     WELL_KNOWN.put(110, "POP3");
        WELL_KNOWN.put(143, "IMAP");    WELL_KNOWN.put(443, "HTTPS");
        WELL_KNOWN.put(445, "SMB");     WELL_KNOWN.put(3306, "MySQL");
        WELL_KNOWN.put(3389, "RDP");    WELL_KNOWN.put(5432, "PostgreSQL");
        WELL_KNOWN.put(8080, "HTTP-alt");
    }
    public static String identify(int port) {
        return WELL_KNOWN.getOrDefault(port, "unknown");
    }
}