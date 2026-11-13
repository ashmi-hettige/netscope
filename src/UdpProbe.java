import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.PortUnreachableException;
import java.net.SocketTimeoutException;

public class UdpProbe {

    public static final String OPEN = "open";
    public static final String CLOSED = "closed";
    public static final String NO_ANSWER = "open|filtered";

    // what one probe found out about one port
    public static class Result {
        public final String state;
        public final String info;
        Result(String state, String info) {
            this.state = state;
            this.info = info;
        }
    }

    // TCP is easy because the handshake answers for us. UDP has no handshake
    // at all, so we send a datagram and work it out from what comes back:
    //   something replies        -> open
    //   ICMP port unreachable    -> closed
    //   silence                  -> open|filtered, we honestly cannot tell
    public static Result probe(String host, int port, int timeout) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();

            // connect() on a UDP socket looks strange because UDP is
            // connectionless, and it does not open anything. what it does is
            // record the peer, and that is what lets the JVM match an incoming
            // ICMP error to this socket and throw PortUnreachableException.
            // without it Java just drops the error and we never see "closed"
            socket.connect(InetAddress.getByName(host), port);
            socket.setSoTimeout(timeout);

            byte[] payload = payloadFor(port);
            byte[] buffer = new byte[1024];

            // we send twice. the ICMP error sometimes arrives after the first
            // receive has already given up, and then a closed port would get
            // recorded as silence. the second send gives it another chance
            for (int round = 0; round < 2; round++) {
                socket.send(new DatagramPacket(payload, payload.length));
                try {
                    DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
                    socket.receive(reply);
                    return new Result(OPEN, describe(reply));
                } catch (PortUnreachableException e) {
                    return new Result(CLOSED, "-");
                } catch (SocketTimeoutException e) {
                    // nothing yet, go round again
                }
            }
            return new Result(NO_ANSWER, "-");

        } catch (PortUnreachableException e) {
            // send() can report it too, not just receive()
            return new Result(CLOSED, "-");
        } catch (IOException e) {
            // bad hostname, no socket, anything else. we cannot say the port
            // is closed just because our own side failed
            return new Result(NO_ANSWER, "-");
        } finally {
            if (socket != null) socket.close();
        }
    }

    // a lot of UDP services ignore anything that is not a proper request.
    // sending four random bytes to port 53 gets you nothing, but a real DNS
    // question gets an answer, so for the ports we know we send the real thing
    public static byte[] payloadFor(int port) {
        switch (port) {
            case 53:
            case 5353:
            case 5355:
                return dnsRootQuery();
            case 123:
                return ntpClientRequest();
            case 161:
            case 162:
                return snmpGetSysDescr();
            case 137:
                return netbiosNodeStatus();
            case 1900:
                return ssdpSearch();
            case 11211:
                return "stats\r\n".getBytes();
            case 69:
                return tftpReadRequest();
            default:
                return new byte[] { 0x0d, 0x0a, 0x0d, 0x0a };
        }
    }

    // a normal DNS question, asking the root zone for its NS records
    private static byte[] dnsRootQuery() {
        return new byte[] {
            0x4e, 0x53,             // transaction id, any two bytes will do
            0x01, 0x00,             // standard query, recursion desired
            0x00, 0x01,             // one question
            0x00, 0x00,             // no answers
            0x00, 0x00,             // no authority records
            0x00, 0x00,             // no additional records
            0x00,                   // the name: just the root, one empty label
            0x00, 0x02,             // type NS
            0x00, 0x01              // class IN
        };
    }

    // NTP version 3 client request. 48 bytes and only the first one matters
    private static byte[] ntpClientRequest() {
        byte[] p = new byte[48];
        p[0] = 0x1b;                // leap 0, version 3, mode 3 means client
        return p;
    }

    // SNMPv1 asking for sysDescr.0 with the community string "public".
    // this is BER encoded, the lengths have to match or it is ignored
    private static byte[] snmpGetSysDescr() {
        return new byte[] {
            0x30, 0x29,                                     // SEQUENCE, 41 bytes follow
            0x02, 0x01, 0x00,                               // version 0 = SNMPv1
            0x04, 0x06, 'p', 'u', 'b', 'l', 'i', 'c',       // community string
            (byte) 0xa0, 0x1c,                              // get-request, 28 bytes follow
            0x02, 0x04, 0x00, 0x00, 0x00, 0x01,             // request id
            0x02, 0x01, 0x00,                               // error status
            0x02, 0x01, 0x00,                               // error index
            0x30, 0x0e,                                     // the list of variables
            0x30, 0x0c,                                     // one variable
            0x06, 0x08, 0x2b, 0x06, 0x01, 0x02,             // OID 1.3.6.1.2.1.1.1.0
            0x01, 0x01, 0x01, 0x00,                         //   which is sysDescr.0
            0x05, 0x00                                      // null, we are asking not telling
        };
    }

    // the same node status query that nbtstat sends. the name is "*" but it
    // has to be written in the encoded form, which is why it looks like this
    private static byte[] netbiosNodeStatus() {
        byte[] p = new byte[50];
        p[0] = 0x12; p[1] = 0x34;   // transaction id
        p[5] = 0x01;                // one question
        p[12] = 0x20;               // encoded name is 32 bytes long
        p[13] = 'C'; p[14] = 'K';   // this pair is the encoded "*"
        for (int i = 15; i < 45; i++) p[i] = 'A';
        p[45] = 0x00;               // end of the name
        p[47] = 0x21;               // type NBSTAT
        p[49] = 0x01;               // class IN
        return p;
    }

    // the discovery message a UPnP client sends. it is plain text, which
    // makes a nice contrast with the binary ones above
    private static byte[] ssdpSearch() {
        String s = "M-SEARCH * HTTP/1.1\r\n"
                 + "HOST: 239.255.255.250:1900\r\n"
                 + "MAN: \"ssdp:discover\"\r\n"
                 + "MX: 1\r\n"
                 + "ST: ssdp:all\r\n\r\n";
        return s.getBytes();
    }

    // ask a TFTP server for a file that will not exist. we do not want the
    // file, we want the error reply, because that proves a server is there
    private static byte[] tftpReadRequest() {
        byte[] name = "netscope.probe".getBytes();
        byte[] p = new byte[4 + name.length + 6];
        p[1] = 0x01;                // opcode 1, read request
        System.arraycopy(name, 0, p, 2, name.length);
        int i = 2 + name.length;
        p[i++] = 0x00;              // filename ends with a zero byte
        byte[] mode = "octet".getBytes();
        System.arraycopy(mode, 0, p, i, mode.length);
        p[i + mode.length] = 0x00;
        return p;
    }

    // most replies are binary, so pull out whatever is readable and say how
    // many bytes came back. same idea as the TCP banner grabber
    private static String describe(DatagramPacket reply) {
        int n = reply.getLength();
        StringBuilder text = new StringBuilder();
        byte[] data = reply.getData();
        for (int i = 0; i < n && text.length() < 40; i++) {
            char ch = (char) data[i];
            if (ch >= 32 && ch < 127) text.append(ch);
        }
        String printable = text.toString().trim();
        if (printable.length() >= 4) return n + " bytes: " + printable;
        return n + " bytes";
    }
}