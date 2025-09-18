#!/bin/bash

# Wireless Communication App - Desktop Runner
echo "Starting Wireless Communication Desktop App..."
echo "This will start the WebSocket server on port 8765"
echo "Android devices can connect to this server for chat"
echo ""

# Check if gradlew exists
if [ ! -f "./gradlew" ]; then
    echo "Error: gradlew not found. Make sure you're in the project root directory."
    exit 1
fi

# Make gradlew executable
chmod +x ./gradlew

# Run the desktop application
echo "Starting desktop application..."
./gradlew :composeApp:run

echo ""
echo "Desktop app stopped."
