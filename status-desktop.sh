#!/bin/bash

# Desktop App Status Script
# This script checks if the desktop app is running

PORT=8765
APP_NAME="Wireless Communication Desktop"

echo "Checking status of $APP_NAME..."

if lsof -i :$PORT >/dev/null 2>&1; then
    PID=$(lsof -ti :$PORT)
    echo "✅ $APP_NAME is RUNNING"
    echo "   Port: $PORT"
    echo "   PID: $PID"
    echo "   Process: $(ps -p $PID -o comm= 2>/dev/null || echo 'Unknown')"
    
    # Check for WebSocket connections
    CONNECTIONS=$(lsof -i :$PORT | grep -c ESTABLISHED)
    echo "   Active connections: $CONNECTIONS"
    
    # Check UDP broadcast
    if netstat -u -n 2>/dev/null | grep -q ":$PORT"; then
        echo "   UDP broadcast: Active"
    else
        echo "   UDP broadcast: Not detected"
    fi
else
    echo "❌ $APP_NAME is NOT RUNNING"
    echo "   Port $PORT is free"
fi
