// NetScope — holds one open-port result. Owner: Member 2.
public class PortResult {
    public final int port;
    public final String service;
    public final String banner;
    public final String state;

    // the TCP scan still uses this one. if a connect worked then the port is
    // open, there is nothing else it could be, so state is filled in for us
    public PortResult(int port, String service, String banner) {
        this(port, service, banner, "open");
    }

    // the UDP scan needs to say which state it found, because a port that
    // stayed quiet is not the same as a port that told us it was closed
    public PortResult(int port, String service, String banner, String state) {
        this.port = port;
        this.service = service;
        this.banner = banner;
        this.state = state;
    }
}

