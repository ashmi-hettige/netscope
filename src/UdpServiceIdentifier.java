import java.util.HashMap;

public class UdpServiceIdentifier {

    private static HashMap<Integer, String> services = new HashMap<>();

    // these are UDP services. port 443 is HTTPS over TCP but QUIC over UDP,
    // so the two tables really are different and I kept them apart
    static {
        services.put(53, "DNS");
        services.put(67, "DHCP-server");
        services.put(68, "DHCP-client");
        services.put(69, "TFTP");
        services.put(123, "NTP");
        services.put(137, "NetBIOS-NS");
        services.put(138, "NetBIOS-DGM");
        services.put(161, "SNMP");
        services.put(162, "SNMP-trap");
        services.put(443, "QUIC");
        services.put(500, "IKE");
        services.put(514, "Syslog");
        services.put(520, "RIP");
        services.put(623, "IPMI");
        services.put(1194, "OpenVPN");
        services.put(1434, "MSSQL-browser");
        services.put(1701, "L2TP");
        services.put(1812, "RADIUS");
        services.put(1900, "SSDP");
        services.put(3478, "STUN");
        services.put(4500, "IPsec-NAT-T");
        services.put(5060, "SIP");
        services.put(5353, "mDNS");
        services.put(5355, "LLMNR");
        services.put(11211, "Memcached");
    }

    // scanning all 65535 UDP ports is not really practical, the reason is
    // explained in PROTOCOL.md. so we offer this shortlist instead
    private static final int[] COMMON = {
        53, 67, 68, 69, 123, 137, 138, 161, 162, 443, 500, 514, 520, 623,
        1194, 1434, 1701, 1812, 1900, 3478, 4500, 5060, 5353, 5355, 11211
    };

    public static String identify(int port) {
        String name = services.get(port);
        if (name == null) return "unknown";
        return name;
    }

    // a copy, so nobody can change our list by accident
    public static int[] commonPorts() {
        return COMMON.clone();
    }
}