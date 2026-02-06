# LoRa Module Control Pins (M0, M1, AUX)
## Complete Guide for ESP32-C6 Integration

---

## 🔌 **LoRa Control Pins Overview**

The LoRa module (E220-900T22D) has three important control pins that determine its operating mode and provide status information:

### **Control Pins:**
- **M0**: Mode Control Pin 0
- **M1**: Mode Control Pin 1  
- **AUX**: Auxiliary Pin (Status Output)

---

## 📋 **LoRa Operating Modes**

| M0 | M1 | Mode | Description | Use Case |
|----|----|------|-------------|----------|
| **0** | **0** | **Normal Mode** | Transparent transmission mode | Data communication |
| **1** | **0** | **Wake-up Mode** | Low power, wake on data | Power saving |
| **0** | **1** | **Power Saving Mode** | Sleep mode, periodic wake | Battery optimization |
| **1** | **1** | **Configuration Mode** | AT command mode | Parameter setup |

---

## 🔧 **Recommended Pin Connections**

### **For ESP32-C6 Integration:**

```
ESP32-C6 Pin    →    LoRa Module Pin    →    Purpose
─────────────────────────────────────────────────────
GPIO2          →    M0                 →    Mode Control 0
GPIO3          →    M1                 →    Mode Control 1
GPIO1          →    AUX                →    Status Output (Optional)
GPIO4          →    TX                 →    UART TX
GPIO5          →    RX                 →    UART RX
3.3V           →    VCC                →    Power Supply
GND            →    GND                →    Common Ground
```

---

## 💡 **Mode Control Logic**

### **Normal Operation (Recommended)**
```cpp
// Set to Normal Mode (M0=0, M1=0)
digitalWrite(M0_PIN, LOW);
digitalWrite(M1_PIN, LOW);
```
- **Use**: Regular data transmission and reception
- **Power**: Normal consumption
- **Best for**: Continuous communication

### **Configuration Mode**
```cpp
// Set to Configuration Mode (M0=1, M1=1)
digitalWrite(M0_PIN, HIGH);
digitalWrite(M1_PIN, HIGH);
delay(100); // Wait for mode change
```
- **Use**: Setting module parameters via AT commands
- **Power**: Configuration state
- **Best for**: Initial setup and parameter changes

### **Power Saving Mode**
```cpp
// Set to Power Saving Mode (M0=0, M1=1)
digitalWrite(M0_PIN, LOW);
digitalWrite(M1_PIN, HIGH);
```
- **Use**: Battery-powered applications
- **Power**: Reduced consumption
- **Best for**: Intermittent communication

---

## 🔄 **AUX Pin Usage**

### **AUX Pin Functions:**
- **Status Indicator**: Shows module activity
- **Data Ready Signal**: Indicates data available
- **Configuration Ready**: Shows config mode active

### **AUX Pin Monitoring:**
```cpp
#define AUX_PIN 1

// Check if module is ready
bool isLoRaReady() {
    return digitalRead(AUX_PIN) == HIGH;
}

// Wait for module to be ready
void waitForLoRaReady() {
    while (!isLoRaReady()) {
        delay(10);
    }
}
```

---

## 📝 **Updated ESP32-C6 Code Integration**

### **Pin Definitions:**
```cpp
// LoRa Control Pins
#define LORA_M0_PIN 2
#define LORA_M1_PIN 3
#define LORA_AUX_PIN 1
#define LORA_TX_PIN 4
#define LORA_RX_PIN 5

// LoRa Module Configuration
SoftwareSerial loraSerial(LORA_RX_PIN, LORA_TX_PIN);
```

### **LoRa Initialization Function:**
```cpp
void initializeLoRaModule() {
    // Set pin modes
    pinMode(LORA_M0_PIN, OUTPUT);
    pinMode(LORA_M1_PIN, OUTPUT);
    pinMode(LORA_AUX_PIN, INPUT);
    
    // Initialize to Normal Mode
    digitalWrite(LORA_M0_PIN, LOW);
    digitalWrite(LORA_M1_PIN, LOW);
    
    // Wait for module to be ready
    delay(1000);
    
    // Configure LoRa parameters
    configureLoRaParameters();
}

void configureLoRaParameters() {
    // Switch to Configuration Mode
    digitalWrite(LORA_M0_PIN, HIGH);
    digitalWrite(LORA_M1_PIN, HIGH);
    delay(100);
    
    // Send configuration commands
    loraSerial.begin(9600);
    loraSerial.println("AT+MODE=3");     // LoRaWAN mode
    delay(100);
    loraSerial.println("AT+BAND=868");   // Frequency band
    delay(100);
    loraSerial.println("AT+ADDR=0001");  // Module address
    delay(100);
    loraSerial.println("AT+NETWORKID=0001"); // Network ID
    delay(100);
    loraSerial.println("AT+PARAMETER=10,7,1,4"); // SF, BW, CR, Preamble
    delay(100);
    
    // Switch back to Normal Mode
    digitalWrite(LORA_M0_PIN, LOW);
    digitalWrite(LORA_M1_PIN, LOW);
    delay(100);
}
```

---

## ⚡ **Power Management**

### **Wake-up from Sleep:**
```cpp
void wakeUpLoRa() {
    // Switch from Power Saving to Normal Mode
    digitalWrite(LORA_M0_PIN, LOW);
    digitalWrite(LORA_M1_PIN, LOW);
    delay(100); // Wait for wake-up
}
```

### **Enter Power Saving:**
```cpp
void enterPowerSaving() {
    // Switch to Power Saving Mode
    digitalWrite(LORA_M0_PIN, LOW);
    digitalWrite(LORA_M1_PIN, HIGH);
    delay(100); // Wait for mode change
}
```

---

## 🔧 **Hardware Connections Diagram**

```
ESP32-C6 Development Board
┌─────────────────────────────────┐
│                                 │
│  GPIO2 ────────────────────────► M0 (LoRa Mode Control 0)
│  GPIO3 ────────────────────────► M1 (LoRa Mode Control 1)
│  GPIO1 ────────────────────────► AUX (LoRa Status Output)
│  GPIO4 ────────────────────────► TX (LoRa UART TX)
│  GPIO5 ────────────────────────► RX (LoRa UART RX)
│  3.3V  ────────────────────────► VCC (LoRa Power)
│  GND   ────────────────────────► GND (LoRa Ground)
│                                 │
└─────────────────────────────────┘
                │
                ▼
    ┌─────────────────────┐
    │   LoRa Module       │
    │   (E220-900T22D)    │
    │                     │
    │  M0 ◄───────────────┼─── Mode Control 0
    │  M1 ◄───────────────┼─── Mode Control 1
    │  AUX ───────────────┼─── Status Output
    │  TX  ◄──────────────┼─── UART TX
    │  RX  ───────────────┼─── UART RX
    │  VCC ◄──────────────┼─── Power Supply
    │  GND ◄──────────────┼─── Common Ground
    │  ANT ───────────────┼─── External Antenna
    └─────────────────────┘
```

---

## ⚠️ **Important Notes**

### **Mode Switching:**
1. **Always wait** 100ms after changing M0/M1 pins
2. **AUX pin** indicates when mode change is complete
3. **Configuration mode** is required for parameter changes
4. **Normal mode** is used for data communication

### **Power Considerations:**
1. **Power Saving Mode** reduces current consumption
2. **Wake-up time** may be needed when exiting sleep
3. **AUX pin** can be used to detect module readiness
4. **Mode switching** consumes minimal power

### **Best Practices:**
1. **Initialize** to Normal Mode for regular operation
2. **Use Configuration Mode** only when changing parameters
3. **Monitor AUX pin** for module status
4. **Implement proper delays** between mode changes

---

## 🎯 **Recommended Configuration for ESP32-C6**

### **For Continuous Operation:**
- **M0**: LOW (GPIO2)
- **M1**: LOW (GPIO3)
- **AUX**: Monitor (GPIO1)
- **Mode**: Normal Mode (00)

### **For Battery-Powered Applications:**
- **M0**: LOW (GPIO2)
- **M1**: HIGH (GPIO3) - Power Saving Mode
- **AUX**: Monitor (GPIO1)
- **Mode**: Power Saving Mode (01)

This configuration provides full control over the LoRa module's operating modes and enables efficient power management for your ESP32-C6 LoRa Mesh Communication System!

---

## 📺 **OLED Display Pin Connections**

### **I2C OLED Display (0.96" 128x64) to ESP32-C6:**

```
OLED Display Pin    →    ESP32-C6 Pin    →    Purpose
─────────────────────────────────────────────────────
VCC                →    3.3V            →    Power Supply
GND                →    GND             →    Common Ground
SDA                →    GPIO7           →    I2C Data Line
SCL                →    GPIO8           →    I2C Clock Line
```

### **OLED Display Specifications:**
- **Type**: 0.96" I2C OLED Display
- **Resolution**: 128x64 pixels
- **Interface**: I2C (2-wire)
- **Address**: 0x3C (default)
- **Power**: 3.3V, ~20mA
- **Features**: Monochrome, high contrast, low power

### **OLED I2C Configuration:**
```cpp
// OLED Display Configuration
#define OLED_SDA_PIN 7      // GPIO7 for SDA
#define OLED_SCL_PIN 8      // GPIO8 for SCL
#define OLED_RESET_PIN -1   // Not connected
#define SCREEN_ADDRESS 0x3C // I2C address (default)

// Initialize I2C for OLED
Wire.begin(OLED_SDA_PIN, OLED_SCL_PIN);
```

### **OLED Display Test Function:**
```cpp
void testOLED() {
  display.clearDisplay();
  display.setCursor(0, 0);
  display.println("ESP32-C6 LoRa");
  display.println("OLED Test OK");
  display.println("Battery: 85%");
  display.println("Mode: MESH");
  display.display();
}
```

---

## 🔌 **Complete System Pin Assignment**

### **All Device Connections Summary:**

| ESP32-C6 Pin | Device | Connection | Purpose |
|--------------|--------|------------|---------|
| **3.3V** | LoRa Module | VCC | Power Supply |
| **3.3V** | OLED Display | VCC | Power Supply |
| **GND** | LoRa Module | GND | Common Ground |
| **GND** | OLED Display | GND | Common Ground |
| **GND** | Battery Monitor | GND | Common Ground |
| **GPIO1** | LoRa Module | AUX | Status Output |
| **GPIO2** | LoRa Module | M0 | Mode Control 0 |
| **GPIO3** | LoRa Module | M1 | Mode Control 1 |
| **GPIO4** | LoRa Module | TX | UART Communication |
| **GPIO5** | LoRa Module | RX | UART Communication |
| **GPIO6** | Status LED | Anode | System Status Indicator |
| **GPIO7** | OLED Display | SDA | I2C Data Line |
| **GPIO8** | OLED Display | SCL | I2C Clock Line |
| **GPIO9** | Mesh LED | Anode | Mesh Network Activity |
| **GPIO10** | Bluetooth LED | Anode | Bluetooth Status |
| **A0 (GPIO1)** | Battery Monitor | Voltage Divider | Battery Voltage Reading |

### **Power Distribution:**
```
ESP32-C6 3.3V ──┬──► LoRa Module VCC
                ├──► OLED Display VCC
                └──► LED Anodes (via resistors)

ESP32-C6 GND ───┬──► LoRa Module GND
                ├──► OLED Display GND
                ├──► Battery Monitor GND
                └──► LED Cathodes
```

This complete pin assignment guide covers all connections needed for your ESP32-C6 LoRa Mesh Communication System with OLED display, battery monitoring, and status LEDs!
