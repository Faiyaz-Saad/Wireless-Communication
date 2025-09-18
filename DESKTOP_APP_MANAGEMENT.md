# Desktop App Management

This document explains how to manage the Wireless Communication Desktop app and avoid "Address already in use" errors.

## Quick Commands

### Start the Desktop App
```bash
./start-desktop.sh
```
This script will:
- Check if port 8765 is already in use
- Ask if you want to stop the existing process
- Start the desktop app safely

### Stop the Desktop App
```bash
./stop-desktop.sh
```
This script will:
- Find the process using port 8765
- Stop it gracefully
- Verify it's stopped

### Check App Status
```bash
./status-desktop.sh
```
This script will show:
- Whether the app is running
- Process ID and details
- Active connections
- UDP broadcast status

## Manual Commands

### Check if Port is in Use
```bash
lsof -i :8765
```

### Kill Process by Port
```bash
kill -9 $(lsof -ti :8765)
```

### Check All Java Processes
```bash
ps aux | grep java | grep -v grep
```

## Troubleshooting

### "Address already in use" Error
1. Run `./status-desktop.sh` to check if app is running
2. If running, use `./stop-desktop.sh` to stop it
3. Then use `./start-desktop.sh` to start it again

### App Won't Start
1. Check if another process is using port 8765
2. Make sure no other Gradle processes are running
3. Try: `./gradlew --stop` to stop all Gradle daemons
4. Then run `./start-desktop.sh`

### Connection Issues
1. Ensure both devices are on the same network
2. Check firewall settings
3. Verify UDP broadcast is working: `timeout 5 nc -u -l -p 8888`

## What the Desktop App Does

1. **WebSocket Server**: Listens on port 8765 with `/ws` endpoint
2. **UDP Broadcast**: Broadcasts "SERVER:8765" every 2 seconds on port 8888
3. **Desktop Client**: Connects to itself for testing
4. **Mobile Discovery**: Mobile app discovers desktop via UDP broadcast

## Network Requirements

- Both devices must be on the same network
- Port 8765 must be available for WebSocket server
- Port 8888 must be available for UDP broadcast
- No firewall blocking these ports
