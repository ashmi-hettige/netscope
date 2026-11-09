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
    Target      : 127.0.0.1
    Range       : 1-1024 (1024 ports)
    Open ports  : 3
    Elapsed     : 812 ms
    ----------------------------------------------
    PORT	SERVICE		BANNER
    22	SSH		SSH-2.0-OpenSSH_8.9
    80	HTTP		-
    REPORT_END

## 4. Error — TCP, server to client
    ERROR <human-readable message>