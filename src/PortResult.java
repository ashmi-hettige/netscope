
/**
 * NetScope - PortResult
 *
 * Stores information about an open TCP port.
 * Owner: Member 2
 */
public class PortResult {

    private final int port;
    private final String service;
    private final String banner;

    public PortResult(int port, String service, String banner) {
        this.port = port;
        this.service = service;
        this.banner = banner;
    }

    public int getPort() {
        return port;
    }

    public String getService() {
        return service;
    }

    public String getBanner() {
        return banner;
    }

    @Override
    public String toString() {
        return "Port: " + port
                + ", Service: " + service
                + ", Banner: " + banner;
    }
}
