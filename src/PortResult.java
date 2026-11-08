// NetScope — holds one open-port result. Owner: Member 2.
public class PortResult {
    public final int port;
    public final String service;
    public final String banner;

    public PortResult(int port, String service, String banner) {
        this.port = port;
        this.service = service;
        this.banner = banner;
    }
}