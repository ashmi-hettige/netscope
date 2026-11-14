# NetScope

A multithreaded network service and port scanner, written in Java.

NetScope is a client-server diagnostic tool. A server process accepts scan
requests over TCP, probes the requested ports concurrently, streams progress
back over UDP, and returns a formatted report. Two clients are provided: a
command-line client and a Swing desktop client.

## Authorised use only

This tool is for checking machines you own or have written permission to test.
Scanning hosts without authorisation is unlawful in most jurisdictions,
including Sri Lanka. All demonstrations in this project were run against
`127.0.0.1` and against the authors' own machines.

## Features

- **TCP connect scanning** over a port range, with a configurable timeout
- **UDP scanning** with three result states: open, closed, and open|filtered
- **Two layers of concurrency**: a thread per client on the server, and a
  fixed thread pool per scan
- **Cancellation** of a running scan from a second connection
- **Configurable** probe timeout and thread pool size per request
- **Service identification** for well-known ports, with separate tables for
  TCP and UDP
- **Banner grabbing** on open TCP ports
- **Live progress** over UDP while a scan runs
- **Report export** as plain text or CSV
- **Two clients**: a CLI and a Swing GUI

## Architecture

```
  ScannerClientGUI  /  ScannerClient
            |                    ^
     TCP    |  request           |  UDP   progress updates
            v                    |
                ScannerServer
                     |
              thread per client
                     |
                ClientHandler
                     |
              fixed thread pool
                /          \
        scanOnePort      UdpProbe
         (TCP connect)   (datagram + ICMP)
```

Both transport protocols are used deliberately, for what each is good at:

- **TCP** carries the request and the final report. These must arrive complete
  and in order, so the reliability is worth the overhead.
- **UDP** carries progress updates. A dropped progress message does not matter,
  and the next one supersedes it, so the speed is worth the unreliability.

## Why a UDP port has three states

TCP tells us whether a port is open because the handshake either completes or
is refused. UDP has no handshake, so the result has to be inferred:

| What comes back | State |
|---|---|
| A reply datagram | `open` |
| ICMP port unreachable | `closed` |
| Nothing | `open|filtered` |

`open|filtered` means the scanner cannot tell. The service may be listening but
may ignore a request it does not recognise, or a firewall may have dropped the
probe. Reporting such a port as simply open or simply closed would be a guess.

Java cannot read ICMP directly without raw sockets. The probe therefore connects
the `DatagramSocket` to the target, which causes the JVM to surface the ICMP
error as a `PortUnreachableException`. See `PROTOCOL.md` sections 9 and 10.

## Measured performance

Scanning is dominated by waiting, so the thread pool matters. These figures were
measured with `Benchmark.java` on one of the authors' own machines.

**Against the loopback interface, 5,100 ports:**

| Threads | Time | Speedup |
|---|---|---|
| 1 | 8,210 ms | 1.0x |
| 10 | 735 ms | 11.2x |
| 50 | 569 ms | 14.4x |
| 200 | 514 ms | 16.0x |

**Against the machine's own Wi-Fi interface, 200 ports:**

| Threads | Time | Speedup |
|---|---|---|
| 1 | 59,499 ms | 1.0x |
| 10 | 6,221 ms | 9.6x |
| 50 | 1,550 ms | 38.4x |
| 200 | 924 ms | 64.4x |

The difference between the two is the finding. On the Wi-Fi interface a filtered
port costs a full 300 ms timeout, so almost all the time is spent blocked and
threads scale nearly linearly. On loopback a closed port is refused immediately,
there is little waiting to overlap, and the speedup flattens out around 16x.

Concurrency pays in proportion to how much time is spent blocked, not to how
much work there is.

## Requirements

Java 17 or later. No external libraries.

## Building

```
javac -d out src/*.java
```

## Running

Start the server first:

```
java -cp out ScannerServer
```

Then either client, in a second terminal:

```
java -cp out ScannerClientGUI
java -cp out ScannerClient
```

On Windows, `run.bat` starts the server and the GUI together.

To reproduce the benchmark figures above:

```
java -cp out Benchmark
```

## Protocol

The client and server speak a small line-based application protocol of our own.
It is specified in `PROTOCOL.md`, which covers the scan request, progress
updates, the report format, errors, optional scan settings, scan ids,
cancellation, and the UDP messages.

The protocol was extended twice during development without breaking older
clients: the optional timeout and thread fields, and the UDP verbs, are both
additions that a five-field TCP client never sends and never has to understand.

## Source files

| File | Purpose |
|---|---|
| `ScannerServer.java` | Accepts connections, one thread per client |
| `ClientHandler.java` | Parses requests, runs the scan, writes the report |
| `UdpProbe.java` | Probes one UDP port and decides its state |
| `ServiceIdentifier.java` | Well-known TCP service names |
| `UdpServiceIdentifier.java` | Well-known UDP service names, common port list |
| `PortResult.java` | One port's result |
| `ScannerClient.java` | Command-line client |
| `ScannerClientGUI.java` | Swing client |
| `ReportWriter.java` | Saves a report as text or CSV |
| `Benchmark.java` | Measures the effect of pool size on scan time |

## Limitations

NetScope is a diagnostic and teaching tool, not a replacement for a
professional scanner. It does not do:

- SYN or other half-open scanning (it uses full TCP connects)
- OS fingerprinting
- Vulnerability detection
- Scanning of multiple hosts or subnets

Exhaustive UDP range scanning of a remote host is impractical because operating
systems rate-limit ICMP error generation, typically to about one per second. The
common-ports mode exists for that reason.

## Authors

Group project for EC5205 Operating Systems and Network Programming,
Department of Electrical and Information Engineering,
Faculty of Engineering, University of Ruhuna.

| Member | Responsibility |
|---|---|
| Imanda H.A., EG/2023/5648         | Server, concurrency, scan engine, benchmarking |
| Jayasuriya M.G.S.F., EG/2023/5665 | Protocol specification, service identification, UDP probing |
| De Zoysa T.T.N., EG/2023/5551     | Clients, user interface, report export |
