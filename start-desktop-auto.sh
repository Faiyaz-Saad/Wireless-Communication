#!/bin/bash

# Desktop App Auto-Start Script (Non-Interactive)
# This script automatically handles the "Address already in use" error

PORT=8765
APP_NAME="Wireless Communication Desktop"

echo "Starting $APP_NAME (Auto Mode)..."

# Function to check if port is in use
check_port() {
    if lsof -i :$PORT >/dev/null 2>&1; then
        return 0  # Port is in use
    else
        return 1  # Port is free
    fi
}

# Function to kill existing process
kill_existing() {
    echo "Found existing process on port $PORT. Stopping it..."
    PID=$(lsof -ti :$PORT)
    if [ ! -z "$PID" ]; then
        kill -9 $PID
        sleep 2
        echo "Stopped existing process (PID: $PID)"
    fi
}

# Check if port is already in use
if check_port; then
    echo "Port $PORT is already in use. Auto-stopping existing process..."
    kill_existing
fi

# Start the desktop app
echo "Starting $APP_NAME on port $PORT..."
./gradlew :composeApp:run
