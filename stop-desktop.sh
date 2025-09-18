#!/bin/bash

# Desktop App Stop Script
# This script stops the desktop app gracefully

PORT=8765
APP_NAME="Wireless Communication Desktop"

echo "Stopping $APP_NAME..."

# Find and kill the process using port 8765
PID=$(lsof -ti :$PORT)

if [ ! -z "$PID" ]; then
    echo "Found process on port $PORT (PID: $PID). Stopping..."
    kill -9 $PID
    sleep 2
    
    # Verify it's stopped
    if lsof -i :$PORT >/dev/null 2>&1; then
        echo "Failed to stop the process. Trying force kill..."
        kill -9 $PID
        sleep 2
    fi
    
    if lsof -i :$PORT >/dev/null 2>&1; then
        echo "Error: Could not stop the process. Please stop it manually."
        exit 1
    else
        echo "$APP_NAME stopped successfully."
    fi
else
    echo "No process found on port $PORT. $APP_NAME is not running."
fi
