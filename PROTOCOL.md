# NetScope Application-Layer Protocol

A small custom protocol over TCP and UDP. All three programs code against these
exact message formats, so they are frozen once agreed.

## 1. Scan request — TCP, client to server
One line, space-separated:
    SCAN <targetIP> <startPort> <endPort> <udpPort>
- targetIP  : host to scan (one you own / may test)
- startPort : first port (1..65535)
- endPort   : last port (>= startPort)
- udpPort   : the client's UDP port to send progress to
Example: SCAN 127.0.0.1 1 1024 53517

## 2. Progress update — UDP, server to client
Sent repeatedly during the scan (loss is acceptable):
    PROGRESS <percent> <openPortsFoundSoFar>
Example: PROGRESS 46 3

## 3. Final report — TCP, server to client
Reliable, ordered, framed by markers:
    REPORT_START
    Scan ID: S1
    Status: complete
    Target: 127.0.0.1
    Range: 1-1024 (1024 ports)
    Scanned: 1024 of 1024
    Settings: timeout 300 ms, 200 threads
    Open ports: 3
    Time: 812 ms
    PORT    SERVICE         BANNER
    22      SSH             SSH-2.0-OpenSSH_8.9
    80      HTTP            -
    REPORT_END

The PORT heading and each row below it are separated by tab characters, not
spaces. The alignment above is only for readability.

Status is "complete" for a scan that ran to the end, or
"CANCELLED - partial results" for one that was stopped. Scanned shows how many
ports were actually checked, which is lower than the range only when the scan
was cancelled.

## 4. Error — TCP, server to client
    ERROR <human-readable message>

## 5. Optional scan settings (backward compatible)

The request may carry two extra fields:

    SCAN <ip> <start> <end> <udpPort> <timeoutMs> <threads>

Five fields means the server uses its defaults (300 ms timeout, 200 threads).
Seven fields means the server uses the values given.
Limits: timeout 50-5000 ms, threads 1-500. Anything outside that gets an ERROR reply.

Old clients that send only five fields keep working, so this is a
backward compatible extension of the protocol.

## 6. Scan id (TCP, server to client)

The server sends this immediately after accepting a valid SCAN request,
before the report, so the client knows which scan to cancel:

    SCANID <id>

Example:

    SCANID S1

## 7. Cancel (TCP, on a second connection)

A client opens a new connection to the server and sends:

    CANCEL <id>

The server replies:

    CANCELLED <id>

or, if no scan with that id is running:

    ERROR No running scan with id <id>

The scan that was cancelled still sends its report, marked as
"CANCELLED - partial results", with a Scanned line showing how many
ports were actually checked before it stopped.
