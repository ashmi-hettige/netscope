@echo off
cd /d "%~dp0"
echo Starting NetScope...
start "NetScope Server" java -cp out ScannerServer
timeout /t 2 /nobreak >nul
java -cp out ScannerClientGUI