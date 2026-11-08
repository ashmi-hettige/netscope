# NetScope – Application Communication Protocol

## 1. Introduction

NetScope uses TCP and UDP communication between the client and server.

- TCP is used to send scan requests and receive final scan results.
- UDP is used to send real-time scan progress updates.

## 2. Scan Request (Client to Server – TCP)

The client sends a scan request using the following format:

`SCAN <ip> <startPort> <endPort> <udpPort>`

Example:

`SCAN 127.0.0.1 1 1000 5000`

Fields:
- `ip`: Target IP address or hostname.
- `startPort`: Starting port number.
- `endPort`: Ending port number.
- `udpPort`: Client UDP port for progress updates.

## 3. Progress Update (Server to Client – UDP)

The server sends progress updates using UDP.

Format:

`PROGRESS <percent> <openCount>`

Example:

`PROGRESS 50 5`

Fields:
- `percent`: Percentage of the scanning process completed.
- `openCount`: Number of open ports discovered so far.

## 4. Final Scan Report (Server to Client – TCP)

The server sends the final scan report through the TCP connection.

The report starts with:

`REPORT_START`

It contains the scan results, followed by:

`REPORT_END`

Each result represents an open port and its identified service.

## 5. Error Message (Server to Client – TCP)

If the server detects an invalid request or an error, it sends:

`ERROR <errorMessage>`

Example:

`ERROR Invalid port range`

## 6. Communication Summary

| Communication | Protocol | Purpose |
|---|---|---|
| Client to Server | TCP | Send scan request |
| Server to Client | UDP | Send live progress updates |
| Server to Client | TCP | Send final scan report |
| Server to Client | TCP | Send error messages |