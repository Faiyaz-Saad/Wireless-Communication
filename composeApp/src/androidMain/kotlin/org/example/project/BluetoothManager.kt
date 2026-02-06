package org.example.project

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class BluetoothManager(private val context: Context) {
    
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard SPP UUID
    private val appName = "WCWI_Chat"
    
    // Server components
    private var serverSocket: BluetoothServerSocket? = null
    private var serverJob: Job? = null
    
    // Client components
    private var clientSocket: BluetoothSocket? = null
    private var clientJob: Job? = null
    
    // Communication
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    
    // Callbacks
    var onMessageReceived: ((String) -> Unit)? = null
    var onConnectionStatusChanged: ((Boolean, String) -> Unit)? = null
    var onDeviceDiscovered: ((BluetoothDevice) -> Unit)? = null
    var onDiscoveryFinished: (() -> Unit)? = null
    
    // State
    private var isConnected = false
    private var isServer = false
    private var isDiscovering = false
    
    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_PERMISSIONS = 2
    }
    
    fun isBluetoothAvailable(): Boolean {
        return bluetoothAdapter != null
    }
    
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
    }
    
    fun getPairedDevices(): List<BluetoothDevice> {
        if (!hasBluetoothPermissions()) return emptyList()
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            println("Permission denied when getting paired devices: ${e.message}")
            emptyList()
        }
    }
    
    fun startDiscovery() {
        if (!hasBluetoothPermissions() || !isBluetoothEnabled()) return
        
        isDiscovering = true
        try {
            bluetoothAdapter?.startDiscovery()
        } catch (e: SecurityException) {
            println("Permission denied when starting discovery: ${e.message}")
            isDiscovering = false
            return
        }
        
        // Monitor discovery completion
        CoroutineScope(Dispatchers.IO).launch {
            while (isDiscovering) {
                try {
                    if (!bluetoothAdapter?.isDiscovering!!) {
                        withContext(Dispatchers.Main) {
                            isDiscovering = false
                            onDiscoveryFinished?.invoke()
                        }
                        break
                    }
                } catch (e: SecurityException) {
                    println("Permission denied when checking discovery status: ${e.message}")
                    break
                }
                delay(1000)
            }
        }
    }
    
    fun stopDiscovery() {
        if (hasBluetoothPermissions()) {
            try {
                bluetoothAdapter?.cancelDiscovery()
            } catch (e: SecurityException) {
                println("Permission denied when stopping discovery: ${e.message}")
            }
        }
        isDiscovering = false
    }
    
    fun startServer(): Boolean {
        if (!hasBluetoothPermissions() || !isBluetoothEnabled()) return false
        
        return try {
            serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(appName, uuid)
            isServer = true
            
            serverJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    while (isServer && !isConnected) {
                        val socket = serverSocket?.accept()
                        if (socket != null) {
                            isConnected = true  // Set connection state before setupConnection
                            setupConnection(socket)
                            withContext(Dispatchers.Main) {
                                println("=== BluetoothManager: Setting isConnected = true ===")
                                onConnectionStatusChanged?.invoke(true, "Client connected")
                            }
                            break // Exit loop after successful connection
                        }
                    }
                } catch (e: IOException) {
                    withContext(Dispatchers.Main) {
                        onConnectionStatusChanged?.invoke(false, "Server error: ${e.message}")
                    }
                }
            }
            true
        } catch (e: IOException) {
            onConnectionStatusChanged?.invoke(false, "Failed to start server: ${e.message}")
            false
        }
    }
    
    fun connectToDevice(device: BluetoothDevice): Boolean {
        if (!hasBluetoothPermissions() || !isBluetoothEnabled()) return false
        
        return try {
            clientSocket = device.createRfcommSocketToServiceRecord(uuid)
            isServer = false
            
            clientJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    clientSocket?.connect()
                    isConnected = true  // Set connection state before setupConnection
                    setupConnection(clientSocket!!)
                    withContext(Dispatchers.Main) {
                        onConnectionStatusChanged?.invoke(true, "Connected to ${device.name}")
                    }
                } catch (e: IOException) {
                    withContext(Dispatchers.Main) {
                        onConnectionStatusChanged?.invoke(false, "Connection failed: ${e.message}")
                    }
                }
            }
            true
        } catch (e: IOException) {
            onConnectionStatusChanged?.invoke(false, "Failed to connect: ${e.message}")
            false
        }
    }
    
    private fun setupConnection(socket: BluetoothSocket) {
        try {
            inputStream = socket.inputStream
            outputStream = socket.outputStream
            
            // Set connection state before starting message listening
            isConnected = true
            println("=== BluetoothManager: Connection established, isConnected = $isConnected ===")
            
            // Start listening for messages
            CoroutineScope(Dispatchers.IO).launch {
                val buffer = ByteArray(1024)
                var messageBuffer = StringBuilder()
                println("=== BluetoothManager: Starting message listening ===")
                println("=== BluetoothManager: isConnected = $isConnected ===")
                
                while (isConnected) {
                    try {
                        val bytes = inputStream?.read(buffer)
                        if (bytes != null && bytes > 0) {
                            val receivedData = String(buffer, 0, bytes)
                            println("=== BluetoothManager: Received raw data ===")
                            println("Bytes received: $bytes")
                            println("Data: $receivedData")
                            messageBuffer.append(receivedData)
                            
                            // Process complete messages (ending with \n)
                            var messageEnd: Int
                            while (messageBuffer.indexOf('\n').also { messageEnd = it } != -1) {
                                val completeMessage = messageBuffer.substring(0, messageEnd).trim()
                                if (completeMessage.isNotEmpty()) {
                                    println("=== BluetoothManager: Processing complete message ===")
                                    println("Complete message: $completeMessage")
                                    withContext(Dispatchers.Main) {
                                        onMessageReceived?.invoke(completeMessage)
                                    }
                                }
                                messageBuffer = StringBuilder(messageBuffer.substring(messageEnd + 1))
                            }
                        } else {
                            println("=== BluetoothManager: No data received or connection lost ===")
                        }
                    } catch (e: IOException) {
                        println("=== BluetoothManager: IOException in message reading ===")
                        println("Error: ${e.message}")
                        break
                    }
                }
                println("=== BluetoothManager: Message listening stopped ===")
            }
        } catch (e: IOException) {
            onConnectionStatusChanged?.invoke(false, "Setup error: ${e.message}")
        }
    }
    
    fun sendMessage(message: String): Boolean {
        return try {
            if (isConnected && outputStream != null) {
                // Add message delimiter to prevent fragmentation
                val messageWithDelimiter = "$message\n"
                println("=== BluetoothManager.sendMessage ===")
                println("Original message: $message")
                println("Message with delimiter: $messageWithDelimiter")
                println("Message bytes: ${messageWithDelimiter.toByteArray().contentToString()}")
                
                outputStream?.write(messageWithDelimiter.toByteArray())
                outputStream?.flush()
                println("Message sent successfully via Bluetooth")
                true
            } else {
                println("Cannot send message - not connected or no output stream")
                println("isConnected: $isConnected, outputStream: ${outputStream != null}")
                false
            }
        } catch (e: IOException) {
            println("Bluetooth send error: ${e.message}")
            e.printStackTrace()
            onConnectionStatusChanged?.invoke(false, "Send error: ${e.message}")
            false
        }
    }
    
    fun disconnect() {
        println("=== BluetoothManager: disconnect() called ===")
        isConnected = false
        isServer = false
        
        try {
            inputStream?.close()
            outputStream?.close()
            clientSocket?.close()
            serverSocket?.close()
        } catch (e: IOException) {
            // Ignore
        }
        
        serverJob?.cancel()
        clientJob?.cancel()
        
        inputStream = null
        outputStream = null
        clientSocket = null
        serverSocket = null
        
        onConnectionStatusChanged?.invoke(false, "Disconnected")
    }
    
    fun isConnected(): Boolean = isConnected
    fun isServer(): Boolean = isServer
    fun isDiscovering(): Boolean = isDiscovering
    
    fun getDeviceName(): String {
        return bluetoothAdapter?.name ?: "Unknown Device"
    }
    
    fun getDeviceAddress(): String {
        return bluetoothAdapter?.address ?: "Unknown Address"
    }
}
