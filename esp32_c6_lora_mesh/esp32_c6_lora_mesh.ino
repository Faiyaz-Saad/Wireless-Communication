/*
 * ESP32-C6 LoRa Mesh Communication System - Enhanced Auto Mode
 * Integrates LoRa mesh networking with Wi-Fi for mobile communication
 * Supports packet fragmentation for large messages and simultaneous send/receive
 * Automatically switches between Direct and Mesh modes based on network conditions
 * 
 * Hardware Requirements:
 * - ESP32-C6 Development Board
 * - LoRa Module (E220-900T22D or similar)
 * - 0.96" OLED Display (I2C)
 * 
 * Connections:
 * LoRa Module -> ESP32-C6:
 *   VCC -> 3.3V
 *   GND -> GND
 *   TX -> GPIO4 (RX)
 *   RX -> GPIO5 (TX)
 *   M0 -> GPIO2
 *   M1 -> GPIO3
 *   AUX -> GPIO1
 * 
 * OLED Display -> ESP32-C6:
 *   VCC -> 3.3V
 *   GND -> GND
 *   SCL -> GPIO8
 *   SDA -> GPIO7
 */

 #include <WiFi.h>
 #include <HardwareSerial.h>
 #include <Wire.h>
 #include <Adafruit_GFX.h>
 #include <Adafruit_SSD1306.h>
 #include <ArduinoJson.h>
 #include <SPIFFS.h>
 
 // ===== Inlined from esp32_c6_config.h =====
 // Hardware Pin Definitions
 // LoRa Module Connections (UART)
 #define LORA_TX_PIN 4        // ESP32-C6 TX -> LoRa RX
 #define LORA_RX_PIN 5        // ESP32-C6 RX -> LoRa TX

 // LoRa Module Control Pins
 #define LORA_M0_PIN 2        // LoRa Mode Control 0
 #define LORA_M1_PIN 3        // LoRa Mode Control 1  
 #define LORA_AUX_PIN 1       // LoRa Status Output (Optional)
 
 // OLED Display Connections (I2C)
 #define OLED_SDA_PIN 7
 #define OLED_SCL_PIN 8
 #define OLED_RESET_PIN -1    // Not connected
 
 // Status LEDs
 #define STATUS_LED_PIN 6
 #define MESH_LED_PIN 9
 #define BT_LED_PIN 10
 
 // Battery Monitoring
 #define BATTERY_PIN A0  // ADC pin for battery voltage monitoring
 #define BATTERY_VOLTAGE_DIVIDER_RATIO 2.0  // Voltage divider ratio (if using voltage divider)
 #define BATTERY_FULL_VOLTAGE 4.2  // Full charge voltage (V)
 #define BATTERY_EMPTY_VOLTAGE 3.0  // Empty battery voltage (V)
 #define BATTERY_CHECK_INTERVAL 5000  // Check battery every 5 seconds
 
 // System Configuration
 #define MAX_PACKET_SIZE 200
 #define MAX_MESSAGE_SIZE 4096
 #define MESH_NODE_ID_LENGTH 8
 #define MAX_ROUTES 20
 #define MAX_MESSAGE_QUEUE 10
 #define PACKET_TIMEOUT 5000
 #define MESSAGE_TIMEOUT 30000
 #define HEARTBEAT_INTERVAL 10000
 #define ROUTE_TIMEOUT 60000
 
 // LoRa Configuration
 #define LORA_FREQUENCY 868000000  // 868MHz for Europe, 915000000 for US
 #define LORA_SPREADING_FACTOR 7
 #define LORA_BANDWIDTH 125000
 #define LORA_CODING_RATE 5
 #define LORA_PREAMBLE_LENGTH 8
 #define LORA_TX_POWER 14
 
 // Network Configuration
 #define WIFI_SSID "ESP32-C6-Mesh"
 #define WIFI_PASSWORD "mesh123456"
 #define TCP_PORT 8080
 
 // Communication Modes
 #define MODE_MESH 1
 #define MODE_DIRECT 2
 #define MODE_AUTO 3
 
 // Debug Configuration
 #define DEBUG_SERIAL Serial
 #define DEBUG_BAUD 115200
 #define DEBUG_LORA false
 #define DEBUG_MESH false
 #define DEBUG_ROUTING false
 // ===== End inlined config =====
 
 // OLED Display Configuration
 #define OLED_WIDTH 128
 #define OLED_HEIGHT 64
 #define OLED_RESET -1
 #define SCREEN_ADDRESS 0x3C
 #define SCREEN_ADDRESS_ALT 0x3D  // Alternative I2C address
 
 // Global OLED state
 bool oledAvailable = false;
 Adafruit_SSD1306 display(OLED_WIDTH, OLED_HEIGHT, &Wire, OLED_RESET);
 
 // LoRa Module Configuration
 HardwareSerial loraSerial(1); // Use UART1 for LoRa
 
 // WiFi Configuration
 const char* ssid = "ESP32-C6-Mesh";
 const char* password = "mesh123456";
 WiFiServer server(8080);
 
 // Message Types - ONLY use enum, NO #define conflicts
 enum MessageType {
   MSG_TEXT = 1,
   MSG_IMAGE = 2,
   MSG_AUDIO = 3,
   MSG_CONTROL = 4,
   MSG_MESH_ROUTE = 5,
   MSG_HEARTBEAT = 6,
   MSG_DIRECT = 7
 };
 
 // Packet Structure
 struct Packet {
   char senderID[MESH_NODE_ID_LENGTH];
   char receiverID[MESH_NODE_ID_LENGTH];
   char messageID[MESH_NODE_ID_LENGTH];
   uint8_t messageType;
   uint16_t totalPackets;
   uint16_t packetNumber;
   uint16_t dataLength;
   uint8_t data[MAX_PACKET_SIZE];
   uint8_t checksum;
 };
 
 // System Variables
 char nodeID[MESH_NODE_ID_LENGTH];
 char currentNodeID[MESH_NODE_ID_LENGTH];
 bool isConnected = false;
 bool isMeshActive = false;
 uint8_t communicationMode = MODE_MESH; // Default to mesh mode
 String incomingMessageBuffer = "";
 String outgoingMessageBuffer = "";
 unsigned long lastHeartbeat = 0;
 unsigned long lastDisplayUpdate = 0;
 
 // Battery Monitoring Variables
 float batteryVoltage = 0.0;
 int batteryPercentage = 0;
 unsigned long lastBatteryCheck = 0;
 
 // Enhanced Auto Mode Variables
 unsigned long lastModeSwitchTime = 0;
 unsigned long modeSwitchInterval = 30000; // Switch mode every 30 seconds if needed
 int directModeSuccessCount = 0;
 int meshModeSuccessCount = 0;
 int directModeFailureCount = 0;
 int meshModeFailureCount = 0;
   float lastDirectModeRSSI = -999;
   float lastMeshModeRSSI = -999;
   bool autoModeEnabled = true;
   unsigned long lastNetworkScan = 0;
   unsigned long networkScanInterval = 10000; // Scan network every 10 seconds
 
 // Message Queue for packet assembly
 struct MessageQueue {
   char messageID[MESH_NODE_ID_LENGTH];
   String data;
   uint16_t totalPackets;
   uint16_t receivedPackets;
   unsigned long timestamp;
   bool isComplete;
 };
 
 MessageQueue messageQueue[10];
 uint8_t queueIndex = 0;
 
 // Mesh Routing Table
 struct MeshRoute {
   char nodeID[MESH_NODE_ID_LENGTH];
   char nextHop[MESH_NODE_ID_LENGTH];
   uint8_t hopCount;
   unsigned long lastSeen;
 };
 
 MeshRoute routingTable[20];
 uint8_t routeCount = 0;
 
 // I2C Scanner Function
 void scanI2C() {
   Serial.println("Scanning I2C devices...");
   byte error, address;
   int nDevices = 0;
   
   for(address = 1; address < 127; address++) {
     Wire.beginTransmission(address);
     error = Wire.endTransmission();
     
     if (error == 0) {
       Serial.print("I2C device found at address 0x");
       if (address < 16) Serial.print("0");
       Serial.print(address, HEX);
       Serial.println(" !");
       nDevices++;
     }
   }
   
   if (nDevices == 0) {
     Serial.println("No I2C devices found");
   } else {
     Serial.print("Found ");
     Serial.print(nDevices);
     Serial.println(" I2C device(s)");
   }
 }
 
 // Initialize OLED with error handling
 bool initializeOLED() {
   Serial.println("Initializing OLED display...");
   
   // Initialize I2C with proper pins
   Wire.begin(OLED_SDA_PIN, OLED_SCL_PIN);
   Wire.setClock(100000); // Set I2C clock to 100kHz (slower for reliability)
   
   delay(100); // Wait for I2C to stabilize
   
   // Scan for I2C devices first
   scanI2C();
   
   // Try to initialize OLED at address 0x3C
   Serial.println("Trying OLED at address 0x3C...");
   if(!display.begin(SSD1306_SWITCHCAPVCC, SCREEN_ADDRESS)) {
     Serial.println("❌ OLED failed at 0x3C, trying 0x3D...");
     
     // Try alternative address 0x3D
     if(!display.begin(SSD1306_SWITCHCAPVCC, SCREEN_ADDRESS_ALT)) {
       Serial.println("❌ OLED failed at both addresses!");
       Serial.println("Check connections:");
       Serial.println("  VCC -> 3.3V");
       Serial.println("  GND -> GND");
       Serial.println("  SDA -> GPIO7");
       Serial.println("  SCL -> GPIO8");
       return false;
     } else {
       Serial.println("✅ OLED OK at address 0x3D");
     }
   } else {
     Serial.println("✅ OLED OK at address 0x3C");
   }
   
   // Test display functionality
   display.clearDisplay();
   display.setTextSize(1);
   display.setTextColor(SSD1306_WHITE);
   display.setCursor(0,0);
   display.println("ESP32-C6 LoRa Mesh");
   display.println("OLED Working!");
   display.display();
   
   delay(1000); // Show test message
   
   return true;
 }
 
 // Safe display update function
 void safeDisplayUpdate(String line1, String line2, String line3, String line4, String line5) {
   if (!oledAvailable) return;
   
   display.clearDisplay();
   display.setCursor(0, 0);
   display.println(line1);
   display.println(line2);
   display.println(line3);
   display.println(line4);
   display.println(line5);
   display.display();
 }
 
 // Enhanced Auto Mode Functions
 void updateCommunicationMode() {
   if (communicationMode != MODE_AUTO) {
     return; // Only work in AUTO mode
   }
   
   unsigned long currentTime = millis();
   
   // Check if it's time to evaluate mode switching
   if (currentTime - lastModeSwitchTime < modeSwitchInterval) {
     return;
   }
   
   lastModeSwitchTime = currentTime;
   
   // Analyze network conditions and decide optimal mode
   int optimalMode = analyzeNetworkConditions();
   
   if (optimalMode != communicationMode) {
     switchToOptimalMode(optimalMode);
   }
 }
 
 int analyzeNetworkConditions() {
   Serial.println("Analyzing network conditions...");
   
   // Calculate success rates
   float directSuccessRate = 0;
   float meshSuccessRate = 0;
   
   if (directModeSuccessCount + directModeFailureCount > 0) {
     directSuccessRate = (float)directModeSuccessCount / (directModeSuccessCount + directModeFailureCount);
   }
   
   if (meshModeSuccessCount + meshModeFailureCount > 0) {
     meshSuccessRate = (float)meshModeSuccessCount / (meshModeSuccessCount + meshModeFailureCount);
   }
   
   Serial.println("Direct success rate: " + String(directSuccessRate * 100, 1) + "%");
   Serial.println("Mesh success rate: " + String(meshSuccessRate * 100, 1) + "%");
   
   // Count active routes in mesh
   int activeMeshRoutes = 0;
   unsigned long currentTime = millis();
   for (int i = 0; i < routeCount; i++) {
     if (currentTime - routingTable[i].lastSeen < 60000) { // Routes seen in last minute
       activeMeshRoutes++;
     }
   }
   
   Serial.println("Active mesh routes: " + String(activeMeshRoutes));
   
   // Decision logic for optimal mode
   int optimalMode = MODE_DIRECT; // Default to direct
   
   // If mesh has more active routes and good success rate, use mesh
   if (activeMeshRoutes > 2 && meshSuccessRate > 0.7) {
     optimalMode = MODE_MESH;
     Serial.println("Choosing MESH mode - good network topology");
   }
   // If direct mode has high success rate and mesh is poor, use direct
   else if (directSuccessRate > 0.8 && meshSuccessRate < 0.5) {
     optimalMode = MODE_DIRECT;
     Serial.println("Choosing DIRECT mode - better direct performance");
   }
   // If both modes are poor, prefer mesh for better coverage
   else if (directSuccessRate < 0.5 && meshSuccessRate < 0.5) {
     optimalMode = MODE_MESH;
     Serial.println("Choosing MESH mode - better coverage when both poor");
   }
   // If direct is significantly better, use direct
   else if (directSuccessRate > meshSuccessRate + 0.3) {
     optimalMode = MODE_DIRECT;
     Serial.println("Choosing DIRECT mode - significantly better performance");
   }
   // Default to mesh for better range
   else {
     optimalMode = MODE_MESH;
     Serial.println("Choosing MESH mode - default for better range");
   }
   
   return optimalMode;
 }
 
 void switchToOptimalMode(int newMode) {
   if (newMode == MODE_DIRECT) {
     communicationMode = MODE_DIRECT;
     Serial.println("🔄 Auto-switched to DIRECT mode");
     if (oledAvailable) {
       displayMessage("Auto: DIRECT");
     }
   } else if (newMode == MODE_MESH) {
     communicationMode = MODE_MESH;
     Serial.println("🔄 Auto-switched to MESH mode");
     if (oledAvailable) {
       displayMessage("Auto: MESH");
     }
   }
   
   // Reset counters when switching modes
   directModeSuccessCount = 0;
   meshModeSuccessCount = 0;
   directModeFailureCount = 0;
   meshModeFailureCount = 0;
 }
 
 void recordCommunicationSuccess(MessageType type) {
   if (communicationMode == MODE_DIRECT) {
     directModeSuccessCount++;
   } else if (communicationMode == MODE_MESH) {
     meshModeSuccessCount++;
   }
 }
 
 void recordCommunicationFailure(MessageType type) {
   if (communicationMode == MODE_DIRECT) {
     directModeFailureCount++;
   } else if (communicationMode == MODE_MESH) {
     meshModeFailureCount++;
   }
 }
 
 void scanNetworkTopology() {
   unsigned long currentTime = millis();
   if (currentTime - lastNetworkScan < networkScanInterval) {
     return;
   }
   
   lastNetworkScan = currentTime;
   
   Serial.println("Scanning network topology...");
   
   // Send discovery packets to map network
   sendNetworkDiscoveryPacket();
   
   // Analyze network density
   int nearbyNodes = 0;
   for (int i = 0; i < routeCount; i++) {
     if (routingTable[i].hopCount == 1) { // Direct neighbors
       nearbyNodes++;
     }
   }
   
   Serial.println("Nearby nodes: " + String(nearbyNodes));
   
   // Adjust mode switching interval based on network stability
   if (nearbyNodes > 3) {
     modeSwitchInterval = 45000; // More stable network, switch less frequently
   } else if (nearbyNodes < 2) {
     modeSwitchInterval = 20000; // Unstable network, switch more frequently
   } else {
     modeSwitchInterval = 30000; // Default interval
   }
 }
 
 void sendNetworkDiscoveryPacket() {
   Packet discovery;
   strcpy(discovery.senderID, nodeID);
   strcpy(discovery.receiverID, "FFFFFFFF"); // Broadcast
   strcpy(discovery.messageID, "DISCOVERY");
   discovery.messageType = MSG_CONTROL;
   discovery.totalPackets = 1;
   discovery.packetNumber = 0;
   discovery.dataLength = 0;
   
   sendPacketViaLoRa(&discovery);
 }
 
 void setup() {
   Serial.begin(115200);
   delay(2000); // Wait for serial to stabilize
   
   Serial.println("\n=== ESP32-C6 LoRa Mesh System ===");
   Serial.println("Initializing system...");
   
   // Initialize OLED Display with error handling
   oledAvailable = initializeOLED();
   if (!oledAvailable) {
     Serial.println("⚠️ OLED not available - continuing without display");
   }
   
   // Initialize LoRa Serial
   Serial.println("Initializing LoRa serial...");
   loraSerial.begin(9600, SERIAL_8N1, LORA_RX_PIN, LORA_TX_PIN);
   
   // Initialize LoRa control pins
   Serial.println("Initializing LoRa control pins...");
   pinMode(LORA_M0_PIN, OUTPUT);
   pinMode(LORA_M1_PIN, OUTPUT);
   pinMode(LORA_AUX_PIN, INPUT);
   
   // Set LoRa to transparent mode (M0=0, M1=0)
   Serial.println("Setting LoRa to transparent mode...");
   digitalWrite(LORA_M0_PIN, LOW);
   digitalWrite(LORA_M1_PIN, LOW);
   
   // Wait for AUX pin to go high (module ready)
   Serial.println("Waiting for LoRa module to be ready...");
   int timeout = 0;
   while (digitalRead(LORA_AUX_PIN) == LOW && timeout < 100) {
     delay(10);
     timeout++;
   }
   
   if (digitalRead(LORA_AUX_PIN) == HIGH) {
     Serial.println("✅ LoRa module ready");
   } else {
     Serial.println("⚠️ LoRa module timeout - continuing anyway");
   }
   
   // Generate unique node ID
   generateNodeID();
   
   // Initialize SPIFFS for message storage
   Serial.println("Initializing SPIFFS...");
   if(!SPIFFS.begin(true)){
     Serial.println("⚠️ SPIFFS Mount Failed - continuing without file storage");
   } else {
     Serial.println("✅ SPIFFS initialized");
   }
   
   // Initialize WiFi Access Point
   Serial.println("Initializing WiFi AP...");
   WiFi.softAP(ssid, password);
   IPAddress IP = WiFi.softAPIP();
   Serial.print("✅ WiFi AP IP: ");
   Serial.println(IP);
   
   // Start TCP Server
   server.begin();
   Serial.println("✅ TCP Server started");
   
   // Initialize LoRa mesh
   initializeLoRaMesh();
   
   // Initialize battery monitoring
   initializeBatteryMonitoring();
   
   // Initialize auto mode
   Serial.println("✅ Auto mode initialized - will automatically switch between DIRECT and MESH");
   
   // Display startup information
   if (oledAvailable) {
     safeDisplayUpdate(
       "Node: " + String(nodeID),
       "WiFi: " + String(ssid),
       "IP: " + IP.toString(),
       "LoRa: Active",
       "System: Ready"
     );
   }
   
   Serial.println("✅ System initialized successfully!");
   Serial.println("Node ID: " + String(nodeID));
   Serial.println("WiFi AP: " + String(ssid));
   Serial.println("IP Address: " + IP.toString());
 }
 
 void loop() {
   // Handle incoming connections
   handleWiFiConnections();
   handleLoRaMesh();
   
   // Update routing table
   updateRoutingTable();
   
   // Send periodic heartbeat
   if (millis() - lastHeartbeat > 10000) {
     sendHeartbeat();
     lastHeartbeat = millis();
   }
   
   // Check battery level
   if (millis() - lastBatteryCheck > BATTERY_CHECK_INTERVAL) {
     updateBatteryLevel();
     lastBatteryCheck = millis();
   }
   
   // Update display
   if (millis() - lastDisplayUpdate > 1000) {
     updateDisplay();
     lastDisplayUpdate = millis();
   }
   
   // Process message queue
   processMessageQueue();
   
   // Enhanced auto mode processing
   if (communicationMode == MODE_AUTO) {
     updateCommunicationMode();
     scanNetworkTopology();
   }
   
   delay(10);
 }
 
 void generateNodeID() {
   uint32_t chipId = 0;
   for(int i = 0; i < 17; i += 8) {
     chipId |= ((ESP.getEfuseMac() >> (40 - i)) & 0xff) << i;
   }
   sprintf(nodeID, "%08X", (unsigned int)chipId);
   strcpy(currentNodeID, nodeID);
 }
 
 void initializeLoRaMesh() {
   Serial.println("Initializing LoRa mesh...");
   
   // Clear any pending data
   while (loraSerial.available()) {
     loraSerial.read();
   }
   
   // Test LoRa module communication
   Serial.println("Testing LoRa module communication...");
   loraSerial.println("AT");
   delay(500);
   
   String response = "";
   while (loraSerial.available()) {
     response += (char)loraSerial.read();
   }
   
   if (response.indexOf("OK") != -1) {
     Serial.println("✅ LoRa module responding");
   } else {
     Serial.println("❌ LoRa module not responding - Check connections!");
     Serial.println("Response: " + response);
     return;
   }
   
   // Configure LoRa module with proper delays
   Serial.println("Configuring LoRa module...");
   
   loraSerial.println("AT+MODE=3"); // Set to LoRaWAN mode
   delay(1000);
   checkLoRaResponse("MODE");
   
   loraSerial.println("AT+BAND=868"); // Set frequency band
   delay(1000);
   checkLoRaResponse("BAND");
   
   loraSerial.println("AT+ADDR=0001"); // Set address
   delay(1000);
   checkLoRaResponse("ADDR");
   
   loraSerial.println("AT+NETWORKID=0001"); // Set network ID
   delay(1000);
   checkLoRaResponse("NETWORKID");
   
   loraSerial.println("AT+PARAMETER=10,7,1,4"); // Set parameters
   delay(1000);
   checkLoRaResponse("PARAMETER");
   
   isMeshActive = true;
   Serial.println("✅ LoRa mesh initialized");
 }
 
 void checkLoRaResponse(String command) {
   String response = "";
   unsigned long timeout = millis() + 2000;
   
   while (millis() < timeout && loraSerial.available()) {
     response += (char)loraSerial.read();
   }
   
   if (response.indexOf("OK") != -1) {
     Serial.println("✅ " + command + " command successful");
   } else if (response.indexOf("ERROR") != -1) {
     Serial.println("❌ " + command + " command failed: " + response);
   } else {
     Serial.println("⚠️ " + command + " command - no response");
   }
 }
 
 void handleWiFiConnections() {
   WiFiClient client = server.available();
   if (client) {
     Serial.println("New WiFi client connected");
     String request = "";
     
     while (client.connected()) {
       if (client.available()) {
         char c = client.read();
         request += c;
         
         if (request.indexOf("\r\n\r\n") != -1) {
           processHTTPRequest(client, request);
           break;
         }
       }
     }
     
     client.stop();
   }
 }
 
 void handleLoRaMesh() {
   if (loraSerial.available()) {
     String loraData = loraSerial.readString();
     loraData.trim();
     
     if (loraData.length() > 0) {
       Serial.println("LoRa received: " + loraData);
       
       // Parse received packet
       Packet packet;
       if (parsePacket(loraData, &packet)) {
         processReceivedPacket(&packet);
       }
     }
   }
 }
 
 bool parsePacket(String data, Packet* packet) {
   // Simple packet parsing - in real implementation, use proper serialization
   if (data.length() < 20) return false;
   
   // Extract packet components (simplified parsing)
   int pos = 0;
   data.substring(pos, pos + MESH_NODE_ID_LENGTH).toCharArray(packet->senderID, MESH_NODE_ID_LENGTH);
   pos += MESH_NODE_ID_LENGTH;
   
   data.substring(pos, pos + MESH_NODE_ID_LENGTH).toCharArray(packet->receiverID, MESH_NODE_ID_LENGTH);
   pos += MESH_NODE_ID_LENGTH;
   
   data.substring(pos, pos + MESH_NODE_ID_LENGTH).toCharArray(packet->messageID, MESH_NODE_ID_LENGTH);
   pos += MESH_NODE_ID_LENGTH;
   
   packet->messageType = data.substring(pos, pos + 2).toInt();
   pos += 2;
   
   packet->totalPackets = data.substring(pos, pos + 4).toInt();
   pos += 4;
   
   packet->packetNumber = data.substring(pos, pos + 4).toInt();
   pos += 4;
   
   packet->dataLength = data.substring(pos, pos + 4).toInt();
   pos += 4;
   
   String payload = data.substring(pos);
   // Fixed min() function call with proper type casting
   payload.toCharArray((char*)packet->data, min((int)payload.length(), MAX_PACKET_SIZE));
   
   return true;
 }
 
 void processReceivedPacket(Packet* packet) {
   // Check if packet is for this node or broadcast
   if (strcmp(packet->receiverID, nodeID) == 0 || strcmp(packet->receiverID, "FFFFFFFF") == 0) {
     // Process message for this node
     processMessagePacket(packet);
     
     // Record successful reception for auto mode
     if (communicationMode == MODE_AUTO) {
       recordCommunicationSuccess((MessageType)packet->messageType);
     }
   } else {
     // Forward packet only in mesh mode
     if (communicationMode == MODE_MESH || communicationMode == MODE_AUTO) {
       forwardPacket(packet);
     }
     // In direct mode, ignore packets not for this node
   }
   
   // Update routing table with sender
   updateRoute(packet->senderID, packet->senderID, 1);
 }
 
 void processMessagePacket(Packet* packet) {
   // Find or create message queue entry
   MessageQueue* queueEntry = findMessageQueue(packet->messageID);
   if (queueEntry == nullptr) {
     queueEntry = createMessageQueue(packet->messageID, packet->totalPackets);
   }
   
   if (queueEntry != nullptr) {
     // Add packet data to queue
     String packetData = String((char*)packet->data);
     queueEntry->data += packetData;
     queueEntry->receivedPackets++;
     
     if (queueEntry->receivedPackets >= queueEntry->totalPackets) {
       queueEntry->isComplete = true;
       processCompleteMessage(queueEntry);
     }
   }
 }
 
 MessageQueue* findMessageQueue(char* messageID) {
   for (int i = 0; i < 10; i++) {
     if (strcmp(messageQueue[i].messageID, messageID) == 0) {
       return &messageQueue[i];
     }
   }
   return nullptr;
 }
 
 MessageQueue* createMessageQueue(char* messageID, uint16_t totalPackets) {
   for (int i = 0; i < 10; i++) {
     if (messageQueue[i].messageID[0] == '\0') {
       strcpy(messageQueue[i].messageID, messageID);
       messageQueue[i].totalPackets = totalPackets;
       messageQueue[i].receivedPackets = 0;
       messageQueue[i].timestamp = millis();
       messageQueue[i].isComplete = false;
       messageQueue[i].data = "";
       return &messageQueue[i];
     }
   }
   return nullptr;
 }
 
 void processCompleteMessage(MessageQueue* queueEntry) {
   Serial.println("Complete message received: " + queueEntry->data);
   
   // Display on OLED if available
   if (oledAvailable) {
     displayMessage(queueEntry->data);
   }
   
   // Save to file
   saveMessageToFile(queueEntry->data);
   
   // Clear queue entry
   queueEntry->messageID[0] = '\0';
 }
 
 void sendMessage(String message, MessageType type) {
   // Generate unique message ID
   char messageID[MESH_NODE_ID_LENGTH];
   sprintf(messageID, "%08X", (unsigned int)millis());
   
   bool success = false;
   
   // Choose communication method based on mode
   if (communicationMode == MODE_DIRECT) {
     success = sendDirectMessage(message, type);
   } else if (communicationMode == MODE_MESH) {
     sendMeshMessage(message, type, messageID);
     success = true; // Assume success for mesh (packets will be tracked separately)
   } else if (communicationMode == MODE_AUTO) {
     // Enhanced auto mode logic
     int currentOptimalMode = analyzeNetworkConditions();
     
     if (currentOptimalMode == MODE_DIRECT) {
       success = sendDirectMessage(message, type);
     } else {
       sendMeshMessage(message, type, messageID);
       success = true;
     }
   }
   
   // Record success/failure for auto mode learning
   if (success) {
     recordCommunicationSuccess(type);
   } else {
     recordCommunicationFailure(type);
   }
   
   // Update auto mode if in AUTO mode
   if (communicationMode == MODE_AUTO) {
     updateCommunicationMode();
   }
 }
 
 void sendMeshMessage(String message, MessageType type, char* messageID) {
   // Fragment message into packets for mesh transmission
   int messageLength = message.length();
   int totalPackets = (messageLength + MAX_PACKET_SIZE - 1) / MAX_PACKET_SIZE;
   
   for (int i = 0; i < totalPackets; i++) {
     Packet packet;
     strcpy(packet.senderID, nodeID);
     strcpy(packet.receiverID, "FFFFFFFF"); // Broadcast for mesh
     strcpy(packet.messageID, messageID);
     packet.messageType = type;
     packet.totalPackets = totalPackets;
     packet.packetNumber = i;
     
     int startPos = i * MAX_PACKET_SIZE;
     int endPos = min(startPos + MAX_PACKET_SIZE, messageLength);
     String packetData = message.substring(startPos, endPos);
     
     packet.dataLength = packetData.length();
     packetData.toCharArray((char*)packet.data, packetData.length() + 1);
     
     // Send packet via LoRa mesh
     sendPacketViaLoRa(&packet);
     
     delay(100); // Small delay between packets
   }
   
   Serial.println("Sent mesh message: " + String(totalPackets) + " packets");
 }
 
 bool sendDirectMessage(String message, MessageType type) {
   // Send message directly without mesh routing
   // Check if we have a direct route to destination
   if (routeCount == 0) {
     Serial.println("No direct routes available, using broadcast");
   }
   
   // For direct mode, send as single packet or minimal fragmentation
   int messageLength = message.length();
   
   if (messageLength <= MAX_PACKET_SIZE) {
     // Single packet direct transmission
     Packet packet;
     strcpy(packet.senderID, nodeID);
     strcpy(packet.receiverID, "FFFFFFFF"); // Broadcast for direct mode
     strcpy(packet.messageID, "DIRECT");
     packet.messageType = MSG_DIRECT;
     packet.totalPackets = 1;
     packet.packetNumber = 0;
     packet.dataLength = messageLength;
     message.toCharArray((char*)packet.data, messageLength + 1);
     
     sendPacketViaLoRa(&packet);
     Serial.println("Sent direct message: " + message);
     return true;
   } else {
     // Multi-packet direct transmission (minimal fragmentation)
     char messageID[MESH_NODE_ID_LENGTH];
     sprintf(messageID, "%08X", (unsigned int)millis());
     
     int totalPackets = (messageLength + MAX_PACKET_SIZE - 1) / MAX_PACKET_SIZE;
     
     for (int i = 0; i < totalPackets; i++) {
       Packet packet;
       strcpy(packet.senderID, nodeID);
       strcpy(packet.receiverID, "FFFFFFFF");
       strcpy(packet.messageID, messageID);
       packet.messageType = MSG_DIRECT;
       packet.totalPackets = totalPackets;
       packet.packetNumber = i;
       
       int startPos = i * MAX_PACKET_SIZE;
       int endPos = min(startPos + MAX_PACKET_SIZE, messageLength);
       String packetData = message.substring(startPos, endPos);
       
       packet.dataLength = packetData.length();
       packetData.toCharArray((char*)packet.data, packetData.length() + 1);
       
       sendPacketViaLoRa(&packet);
       delay(50); // Shorter delay for direct mode
     }
     
     Serial.println("Sent direct message: " + String(totalPackets) + " packets");
     return true;
   }
 }
 
 void sendPacketViaLoRa(Packet* packet) {
   // Serialize packet for transmission
   String packetString = String(packet->senderID) + 
                        String(packet->receiverID) + 
                        String(packet->messageID) + 
                        String(packet->messageType, HEX) + 
                        String(packet->totalPackets) + 
                        String(packet->packetNumber) + 
                        String(packet->dataLength) + 
                        String((char*)packet->data);
   
   // Send via LoRa with response checking
   loraSerial.println("AT+SEND=" + String(packetString.length()) + "," + packetString);
   
   // Wait for response
   delay(100);
   String response = "";
   unsigned long timeout = millis() + 1000; // 1 second timeout
   
   while (millis() < timeout && loraSerial.available()) {
     response += (char)loraSerial.read();
   }
   
   if (response.indexOf("OK") != -1) {
     Serial.println("✅ Sent packet " + String(packet->packetNumber) + "/" + String(packet->totalPackets) + " - Success");
   } else if (response.indexOf("ERROR") != -1) {
     Serial.println("❌ Sent packet " + String(packet->packetNumber) + "/" + String(packet->totalPackets) + " - Error: " + response);
   } else {
     Serial.println("⚠️ Sent packet " + String(packet->packetNumber) + "/" + String(packet->totalPackets) + " - No response");
   }
 }
 
 void forwardPacket(Packet* packet) {
   // Find route to destination
   MeshRoute* route = findRoute(packet->receiverID);
   if (route != nullptr) {
     // Forward to next hop
     sendPacketViaLoRa(packet);
     Serial.println("Forwarded packet to: " + String(route->nextHop));
   }
 }
 
 MeshRoute* findRoute(char* destinationID) {
   for (int i = 0; i < routeCount; i++) {
     if (strcmp(routingTable[i].nodeID, destinationID) == 0) {
       return &routingTable[i];
     }
   }
   return nullptr;
 }
 
 void updateRoute(char* nodeID, char* nextHop, uint8_t hopCount) {
   for (int i = 0; i < routeCount; i++) {
     if (strcmp(routingTable[i].nodeID, nodeID) == 0) {
       strcpy(routingTable[i].nextHop, nextHop);
       routingTable[i].hopCount = hopCount;
       routingTable[i].lastSeen = millis();
       return;
     }
   }
   
   // Add new route
   if (routeCount < 20) {
     strcpy(routingTable[routeCount].nodeID, nodeID);
     strcpy(routingTable[routeCount].nextHop, nextHop);
     routingTable[routeCount].hopCount = hopCount;
     routingTable[routeCount].lastSeen = millis();
     routeCount++;
   }
 }
 
 void updateRoutingTable() {
   unsigned long currentTime = millis();
   for (int i = 0; i < routeCount; i++) {
     if (currentTime - routingTable[i].lastSeen > 60000) { // 60 second timeout
       // Remove stale route
       for (int j = i; j < routeCount - 1; j++) {
         routingTable[j] = routingTable[j + 1];
       }
       routeCount--;
       i--;
     }
   }
 }
 
 void sendHeartbeat() {
   Packet heartbeat;
   strcpy(heartbeat.senderID, nodeID);
   strcpy(heartbeat.receiverID, "FFFFFFFF"); // Broadcast
   strcpy(heartbeat.messageID, "HEARTBEAT");
   heartbeat.messageType = MSG_CONTROL;
   heartbeat.totalPackets = 1;
   heartbeat.packetNumber = 0;
   heartbeat.dataLength = 0;
   
   sendPacketViaLoRa(&heartbeat);
 }
 
 void processMessageQueue() {
   unsigned long currentTime = millis();
   for (int i = 0; i < 10; i++) {
     if (messageQueue[i].messageID[0] != '\0' && 
         currentTime - messageQueue[i].timestamp > MESSAGE_TIMEOUT) {
       // Remove expired message
       messageQueue[i].messageID[0] = '\0';
     }
   }
 }
 
 void processCommand(String command) {
   if (command == "STATUS") {
     String modeStr = "";
     if (communicationMode == MODE_MESH) modeStr = "MESH";
     else if (communicationMode == MODE_DIRECT) modeStr = "DIRECT";
     else if (communicationMode == MODE_AUTO) modeStr = "AUTO";
     
     String status = "NodeID:" + String(nodeID) + 
                    ",Mode:" + modeStr +
                    ",Battery:" + String(batteryPercentage) + "%" +
                    ",Voltage:" + String(batteryVoltage, 2) + "V" +
                    ",Routes:" + String(routeCount) + 
                    ",Mesh:" + String(isMeshActive ? "ON" : "OFF");
     
     // Add auto mode statistics
     if (communicationMode == MODE_AUTO) {
       status += ",DirectSuccess:" + String(directModeSuccessCount) +
                 ",MeshSuccess:" + String(meshModeSuccessCount) +
                 ",DirectFail:" + String(directModeFailureCount) +
                 ",MeshFail:" + String(meshModeFailureCount);
     }
     
     Serial.println("STATUS:" + status);
   } else if (command == "BATTERY") {
     String batteryInfo = "Battery:" + String(batteryPercentage) + "%" +
                         ",Voltage:" + String(batteryVoltage, 2) + "V";
     Serial.println("BATTERY:" + batteryInfo);
   } else if (command == "MODE_MESH") {
     communicationMode = MODE_MESH;
     Serial.println("MODE:MESH");
     Serial.println("Switched to MESH mode");
   } else if (command == "MODE_DIRECT") {
     communicationMode = MODE_DIRECT;
     Serial.println("MODE:DIRECT");
     Serial.println("Switched to DIRECT mode");
   } else if (command == "MODE_AUTO") {
     communicationMode = MODE_AUTO;
     Serial.println("MODE:AUTO");
     Serial.println("Switched to AUTO mode");
   } else if (command == "RESTART") {
     ESP.restart();
   }
 }
 
 void processHTTPRequest(WiFiClient client, String request) {
   if (request.indexOf("GET /status") != -1) {
     // Return JSON status
     String modeStr = "";
     if (communicationMode == MODE_MESH) modeStr = "MESH";
     else if (communicationMode == MODE_DIRECT) modeStr = "DIRECT";
     else if (communicationMode == MODE_AUTO) modeStr = "AUTO";
     
     String response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n";
     response += "{";
     response += "\"nodeId\":\"" + String(nodeID) + "\",";
     response += "\"communicationMode\":\"" + modeStr + "\",";
     response += "\"batteryPercentage\":" + String(batteryPercentage) + ",";
     response += "\"batteryVoltage\":" + String(batteryVoltage, 2) + ",";
     response += "\"routeCount\":" + String(routeCount) + ",";
     response += "\"clientCount\":" + String(WiFi.softAPgetStationNum()) + ",";
     response += "\"meshActive\":" + String(isMeshActive ? "true" : "false") + ",";
     response += "\"oledAvailable\":" + String(oledAvailable ? "true" : "false");
     
     // Add auto mode statistics
     if (communicationMode == MODE_AUTO) {
       response += ",\"directSuccessCount\":" + String(directModeSuccessCount) + ",";
       response += "\"meshSuccessCount\":" + String(meshModeSuccessCount) + ",";
       response += "\"directFailureCount\":" + String(directModeFailureCount) + ",";
       response += "\"meshFailureCount\":" + String(meshModeFailureCount);
     }
     
     response += "}";
     
     client.print(response);
   } else if (request.indexOf("POST /mode") != -1) {
     // Handle mode switching via HTTP
     int modeStart = request.indexOf("mode=") + 5;
     int modeEnd = request.indexOf(" ", modeStart);
     String mode = request.substring(modeStart, modeEnd);
     mode.trim();
     
     if (mode == "mesh") {
       communicationMode = MODE_MESH;
     } else if (mode == "direct") {
       communicationMode = MODE_DIRECT;
     } else if (mode == "auto") {
       communicationMode = MODE_AUTO;
     }
     
     String response = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n";
     response += "<html><body><h1>Mode Changed</h1><p>Communication mode set to: " + mode + "</p><a href='/'>Back</a></body></html>";
     client.print(response);
   } else if (request.indexOf("GET /") != -1) {
     // Serve simple web interface
     String response = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n";
     response += "<html><body>";
     response += "<h1>ESP32-C6 LoRa Mesh</h1>";
     response += "<p>Node ID: " + String(nodeID) + "</p>";
     response += "<p>Battery: " + String(batteryPercentage) + "% (" + String(batteryVoltage, 2) + "V)</p>";
     response += "<p>Routes: " + String(routeCount) + "</p>";
     response += "<p>OLED: " + String(oledAvailable ? "Working" : "Not Available") + "</p>";
     response += "<p>Mode: " + String(communicationMode == MODE_AUTO ? "AUTO (Intelligent)" : (communicationMode == MODE_MESH ? "MESH" : "DIRECT")) + "</p>";
     response += "<form method='POST' action='/send'>";
     response += "<input type='text' name='message' placeholder='Enter message'>";
     response += "<input type='submit' value='Send'>";
     response += "</form>";
     response += "</body></html>";
     
     client.print(response);
   } else if (request.indexOf("POST /send") != -1) {
     // Process message from web interface
     int messageStart = request.indexOf("message=") + 8;
     int messageEnd = request.indexOf(" ", messageStart);
     String message = request.substring(messageStart, messageEnd);
     message.replace('+', ' ');
     message.replace("%20", " ");
     
     sendMessage(message, MSG_TEXT);
     
     String response = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n";
     response += "<html><body><h1>Message Sent</h1><a href='/'>Back</a></body></html>";
     client.print(response);
   }
 }
 
 void updateDisplay() {
   if (!oledAvailable) return;
   
   // First line: Node ID and Battery
   String line1 = "Node: " + String(nodeID) + " Bat:" + String(batteryPercentage) + "%";
   
   // Second line: Routes and Clients
   String line2 = "Routes: " + String(routeCount) + " Clients: " + String(WiFi.softAPgetStationNum());
   
   // Third line: Communication mode and status
   String modeStr = "";
   if (communicationMode == MODE_MESH) modeStr = "MESH";
   else if (communicationMode == MODE_DIRECT) modeStr = "DIRECT";
   else if (communicationMode == MODE_AUTO) {
     modeStr = "AUTO";
     // Show which mode is currently active in auto mode
     int currentOptimalMode = analyzeNetworkConditions();
     if (currentOptimalMode == MODE_DIRECT) {
       modeStr += "(DIR)";
     } else {
       modeStr += "(MESH)";
     }
   }
   
   String line3 = "Mode: " + modeStr + " WiFi: " + String(WiFi.softAPgetStationNum() > 0 ? "ON" : "OFF");
   
   // Fourth line: Battery voltage
   String line4 = "Voltage: " + String(batteryVoltage, 2) + "V";
   
   // Fifth line: System status or auto mode stats
   String line5 = "";
   if (communicationMode == MODE_AUTO) {
     line5 = "D:" + String(directModeSuccessCount) + " M:" + String(meshModeSuccessCount);
   } else if (batteryPercentage < 20) {
     line5 = "LOW BATTERY WARNING!";
   } else {
     line5 = "System: OK";
   }
   
   safeDisplayUpdate(line1, line2, line3, line4, line5);
 }
 
 void displayMessage(String message) {
   if (!oledAvailable) return;
   
   display.clearDisplay();
   display.setCursor(0, 0);
   display.println("New Message:");
   if (message.length() > 16) {
     display.println(message.substring(0, 16));
     display.println(message.substring(16, 32));
   } else {
     display.println(message);
   }
   display.display();
   delay(3000);
 }
 
 void saveMessageToFile(String message) {
   File file = SPIFFS.open("/messages.txt", FILE_APPEND);
   if (file) {
     file.println(millis() + ": " + message);
     file.close();
   }
 }
 
 // Battery Monitoring Functions
 void initializeBatteryMonitoring() {
   Serial.println("Initializing battery monitoring...");
   // Initialize ADC for battery monitoring
   analogReadResolution(12); // Set ADC resolution to 12 bits (0-4095)
   analogSetAttenuation(ADC_11db); // Set attenuation for 0-3.3V range
   
   // Initial battery reading
   updateBatteryLevel();
   
   Serial.println("✅ Battery monitoring initialized");
   Serial.println("Initial battery: " + String(batteryVoltage) + "V (" + String(batteryPercentage) + "%)");
 }
 
 void updateBatteryLevel() {
   // Read raw ADC value
   int rawValue = analogRead(BATTERY_PIN);
   
   // Convert to voltage
   // For ESP32-C6: 3.3V reference, 12-bit ADC (0-4095)
   batteryVoltage = (rawValue * 3.3) / 4095.0;
   
   // Apply voltage divider ratio if used
   batteryVoltage = batteryVoltage * BATTERY_VOLTAGE_DIVIDER_RATIO;
   
   // Calculate battery percentage
   batteryPercentage = calculateBatteryPercentage(batteryVoltage);
   
   // Debug output
   if (DEBUG_MESH) {
     Serial.println("Battery: " + String(batteryVoltage, 2) + "V (" + String(batteryPercentage) + "%)");
   }
   
   // Check for low battery warning
   if (batteryPercentage < 20 && batteryPercentage > 0) {
     displayLowBatteryWarning();
   }
 }
 
 int calculateBatteryPercentage(float voltage) {
   // Handle voltage divider compensation
   float actualVoltage = voltage;
   
   // Linear interpolation between empty and full voltages
   if (actualVoltage <= BATTERY_EMPTY_VOLTAGE) {
     return 0;
   } else if (actualVoltage >= BATTERY_FULL_VOLTAGE) {
     return 100;
   } else {
     // Linear mapping between empty and full voltage
     float percentage = ((actualVoltage - BATTERY_EMPTY_VOLTAGE) / 
                        (BATTERY_FULL_VOLTAGE - BATTERY_EMPTY_VOLTAGE)) * 100;
     return (int)percentage;
   }
 }
 
 void displayLowBatteryWarning() {
   if (!oledAvailable) return;
   
   // Flash display for low battery warning
   static unsigned long lastWarningFlash = 0;
   static bool warningState = false;
   
   if (millis() - lastWarningFlash > 500) { // Flash every 500ms
     warningState = !warningState;
     lastWarningFlash = millis();
     
     if (warningState) {
       display.clearDisplay();
       display.setTextSize(1);
       display.setTextColor(SSD1306_WHITE);
       display.setCursor(0, 20);
       display.println("LOW BATTERY!");
       display.println("Voltage: " + String(batteryVoltage, 2) + "V");
       display.println("Charge: " + String(batteryPercentage) + "%");
       display.display();
     } else {
       updateDisplay(); // Show normal display
     }
   }
 }
 
 float getBatteryVoltage() {
   return batteryVoltage;
 }
 
 int getBatteryPercentage() {
   return batteryPercentage;
 }