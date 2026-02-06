# ESP32-C6 LoRa Mesh Communication System

## Overview
This system creates a long-range wireless communication network using ESP32-C6 microcontrollers, LoRa modules, and OLED displays. It supports both direct device-to-device communication and mesh networking for extended range.

## Features
- **Dual Communication Modes**: Mesh networking AND direct communication
- **LoRa Mesh Networking**: Automatic routing and packet forwarding
- **Direct Communication**: Point-to-point communication without mesh routing
- **Auto Mode**: Intelligent switching between mesh and direct modes
- **Packet Fragmentation**: Large messages split into manageable packets
- **Multiple Interfaces**: WiFi AP, Bluetooth, and Web interface
- **Real-time Display**: OLED status and message display with battery monitoring
- **Simultaneous Send/Receive**: Full-duplex communication
- **Auto-Routing**: Dynamic mesh routing with heartbeat monitoring

## Hardware Requirements

### Required Components
1. **ESP32-C6 Development Board** (16MB or 8MB Flash)
2. **LoRa Module** (E220-900T22D or similar)
3. **0.96" OLED Display** (I2C, 128x64)
4. **Connecting wires**
5. **Power supply** (5V, 1A recommended)

### Pin Connections

```
ESP32-C6 Pin    |  LoRa Module  |  OLED Display  |  Battery Monitor
----------------|---------------|----------------|----------------
3.3V           |  VCC          |  VCC           |  -
GND            |  GND          |  GND           |  GND
GPIO4          |  TX           |  -             |  -
GPIO5          |  RX           |  -             |  -
GPIO7          |  -            |  SDA           |  -
GPIO8          |  -            |  SCL           |  -
GPIO6          |  Status LED   |  -             |  -
GPIO9          |  Mesh LED     |  -             |  -
GPIO10         |  BT LED       |  -             |  -
A0             |  -            |  -             |  Battery Voltage
```

### Battery Monitoring Setup
For battery monitoring, connect the battery positive terminal to a voltage divider circuit:
- **Battery Positive** → **Resistor 1 (10kΩ)** → **A0 (GPIO1)**
- **A0 (GPIO1)** → **Resistor 2 (10kΩ)** → **GND**
- **Battery Negative** → **GND**

This creates a 2:1 voltage divider to safely monitor battery voltage up to 6.6V.

## Software Setup

### 1. Arduino IDE Configuration
1. Install Arduino IDE (latest version)
2. Add ESP32 board manager URL:
   ```
   https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json
   ```
3. Install ESP32 board package
4. Select board: **ESP32C6 Dev Module**

### 2. Required Libraries
Install these libraries through Library Manager:
- **Adafruit SSD1306** by Adafruit
- **Adafruit GFX Library** by Adafruit  
- **ArduinoJson** by Benoit Blanchon

### 3. Board Settings
```
Board: ESP32C6 Dev Module
Upload Speed: 921600
CPU Frequency: 240MHz (WiFi/BT)
Flash Size: 4MB (32Mb)
Partition Scheme: Default 4MB with spiffs
```

## Installation Steps

### 1. Hardware Assembly
1. Connect LoRa module to ESP32-C6 using the pin mapping above
2. Connect OLED display to I2C pins (GPIO7, GPIO8)
3. Connect status LEDs to respective GPIO pins
4. Ensure proper power supply (3.3V for all components)

### 2. Software Upload
1. Open `esp32_c6_lora_mesh.ino` in Arduino IDE
2. Verify all libraries are installed
3. Select correct board and port
4. Compile and upload to ESP32-C6

### 3. Configuration
1. Power on the device
2. Wait for initialization (OLED will show startup sequence)
3. Connect to WiFi AP: **ESP32-C6-Mesh** (password: mesh123456)
4. Open web interface at: `http://192.168.4.1:8080`

## Usage Instructions

### Mobile Device Connection
1. **WiFi**: Connect to "ESP32-C6-Mesh" network
2. **Bluetooth**: Pair with "ESP32-C6-Mesh" device
3. **Web Interface**: Open browser to device IP address

### Communication Modes
The system supports three communication modes:

#### 1. **Mesh Mode** (Default)
- Messages are automatically routed through the mesh network
- Supports multi-hop communication
- Best for extended range and network reliability
- Automatic packet fragmentation and reassembly

#### 2. **Direct Mode**
- Point-to-point communication without mesh routing
- Lower latency and overhead
- Best for direct device-to-device communication
- Minimal packet fragmentation

#### 3. **Auto Mode**
- Intelligently switches between mesh and direct modes
- Attempts direct communication first, falls back to mesh if needed
- Best for mixed network scenarios

### Mode Switching
- **Web Interface**: Use mode control buttons
- **Bluetooth**: Send commands:
  - `COMMAND:MODE_MESH` - Switch to mesh mode
  - `COMMAND:MODE_DIRECT` - Switch to direct mode  
  - `COMMAND:MODE_AUTO` - Switch to auto mode
- **HTTP**: POST to `/mode` endpoint with `mode=mesh|direct|auto`

### Sending Messages
1. **Via Web Interface**: Use the web form to send messages
2. **Via Bluetooth**: Send "SEND:your message" to paired device
3. **Via WiFi**: POST to `/send` endpoint

### Mesh Network Setup
1. Deploy multiple ESP32-C6 units with LoRa modules
2. Each unit automatically discovers and routes to others
3. Messages automatically find best path through mesh
4. Add intermediate nodes to extend range

### Message Types Supported
- **Short Text**: Direct transmission
- **Long Text**: Automatic packet fragmentation
- **Images**: Base64 encoded, packetized
- **Audio**: Binary data, packetized

## API Endpoints

### HTTP Endpoints
- `GET /` - Web interface
- `POST /send` - Send message (form data: message=text)
- `GET /status` - Get system status (JSON)
- `POST /mode` - Change communication mode (form data: mode=mesh|direct|auto)
- `POST /restart` - Restart system

### Bluetooth Commands
- `SEND:message` - Send text message
- `COMMAND:STATUS` - Get system status
- `COMMAND:BATTERY` - Get battery information
- `COMMAND:MODE_MESH` - Switch to mesh mode
- `COMMAND:MODE_DIRECT` - Switch to direct mode
- `COMMAND:MODE_AUTO` - Switch to auto mode
- `COMMAND:RESTART` - Restart system

## Troubleshooting

### Common Issues

1. **LoRa Module Not Responding**
   - Check power supply (3.3V)
   - Verify TX/RX connections are correct
   - Ensure LoRa module is properly configured

2. **OLED Display Not Working**
   - Check I2C connections (SDA/SCL)
   - Verify display address (0x3C)
   - Check power supply

3. **WiFi Connection Issues**
   - Check if AP is broadcasting
   - Verify password is correct
   - Try different device to test AP

4. **Mesh Communication Problems**
   - Ensure LoRa modules are on same frequency
   - Check antenna connections
   - Verify mesh routing table

### Debug Mode
Enable debug output by setting these flags in the code:
```cpp
#define DEBUG_LORA true
#define DEBUG_MESH true
#define DEBUG_ROUTING true
```

## Network Topology

### Direct Communication Mode
```
Mobile Device ←→ ESP32-C6 ←→ LoRa (Direct) ←→ LoRa ←→ ESP32-C6 ←→ Mobile Device
```

### Mesh Communication Mode
```
Device A ←→ Node 1 ←→ Node 2 ←→ Node 3 ←→ Device B
              ↓
            Node 4 ←→ Node 5 ←→ Device C
```

### Auto Mode (Hybrid)
```
Direct: Device A ←→ Node 1 ←→ Device B (if in range)
Mesh:   Device A ←→ Node 1 ←→ Node 2 ←→ Device B (if direct fails)
```

## Performance Specifications

- **LoRa Range**: Up to 5km (line of sight)
- **Message Size**: Up to 4KB per message
- **Packet Size**: 200 bytes per packet
- **Mesh Hops**: Up to 20 nodes
- **Battery Life**: ~8 hours (with 1000mAh battery)
- **Update Rate**: 10Hz for mesh routing

## Safety and Compliance

- **Frequency**: 868MHz (Europe) / 915MHz (US)
- **Power**: < 14dBm (25mW) maximum
- **Antenna**: External antenna recommended for range
- **Regulations**: Check local radio regulations

## Support and Maintenance

### Regular Maintenance
- Monitor mesh routing table
- Check power supply voltage
- Clean antenna connections
- Update firmware as needed

### Extending the System
- Add more ESP32-C6 nodes for larger mesh
- Implement GPS for location-aware routing
- Add sensors for environmental monitoring
- Integrate with cloud services

## License
This project is open source. Please ensure compliance with local radio regulations when deploying.
