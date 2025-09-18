#!/bin/bash

# Desktop App Startup Script
# This script handles the "Address already in use" error gracefully

PORT=8765
APP_NAME="Wireless Communication Desktop"

echo "Starting $APP_NAME..."

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
    echo "Port $PORT is already in use."
    read -p "Do you want to stop the existing process and start a new one? (y/n): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        kill_existing
    else
        echo "Exiting. Please stop the existing process manually or choose 'y' to restart."
        exit 1
    fi
fi

# Start the desktop app
echo "Starting $APP_NAME on port $PORT..."
./gradlew :composeApp:run

echo "$APP_NAME stopped."
