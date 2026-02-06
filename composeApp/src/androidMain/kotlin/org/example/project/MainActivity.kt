package org.example.project

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.DhcpInfo
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import chat.platform.platformHttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.util.Base64

// Global variables to store WebSocket sessions
var serverClients = mutableSetOf<DefaultWebSocketServerSession>()
var clientSession: DefaultClientWebSocketSession? = null

// Global message state for sharing between WebSocket handlers and UI
var globalMessages = mutableListOf<ChatMessage>()
var onMessageReceived: ((ChatMessage) -> Unit)? = null
var sentMessageIds = mutableSetOf<String>()
var receivedMessageIds = mutableSetOf<String>()
var isProcessingReceivedMessage = false

// Global storage manager
lateinit var globalStorageManager: MessageStorageManager

// Function to send image from server to all clients
suspend fun sendImageToClients(imageMessage: String) {
    println("=== SERVER SENDING IMAGE TO CLIENTS ===")
    println("Image message: $imageMessage")
    println("Number of connected clients: ${serverClients.size}")
    
    val deadClients = mutableSetOf<DefaultWebSocketServerSession>()
    serverClients.forEach { client ->
        try {
            client.send(Frame.Text(imageMessage))
            println("Successfully sent image to client")
        } catch (e: Exception) {
            println("Error sending image to client: ${e.message}")
            deadClients.add(client)
        }
    }
    serverClients.removeAll(deadClients)
    println("=== SERVER IMAGE SENDING COMPLETE ===")
}

// Function to send image from client to server
suspend fun sendImageToServer(imageMessage: String) {
    println("=== CLIENT SENDING IMAGE TO SERVER ===")
    println("Image message: $imageMessage")
    
    clientSession?.let { session ->
        try {
            session.send(Frame.Text(imageMessage))
            println("Successfully sent image to server")
        } catch (e: Exception) {
            println("Error sending image to server: ${e.message}")
        }
    } ?: run {
        println("Client session does not exist")
    }
    println("=== CLIENT IMAGE SENDING COMPLETE ===")
}

// Function to send message from server to all clients
suspend fun sendMessageToClients(message: String) {
    println("=== SERVER SENDING TO CLIENTS ===")
    println("Message: $message")
    println("Number of connected clients: ${serverClients.size}")
    
    val deadClients = mutableSetOf<DefaultWebSocketServerSession>()
    serverClients.forEach { client ->
        try {
            // Send the message as-is, don't add "Server:" prefix to avoid confusion
            client.send(Frame.Text(message))
            println("Successfully sent to client")
        } catch (e: Exception) {
            println("Error sending to client: ${e.message}")
            deadClients.add(client)
        }
    }
    serverClients.removeAll(deadClients)
    println("=== SERVER SENDING COMPLETE ===")
}

// Function to send message from client to server
suspend fun sendMessageToServer(message: String) {
    println("=== CLIENT SENDING TO SERVER ===")
    println("Message: $message")
    println("Client session exists: ${clientSession != null}")
    
    try {
        // Send the message as-is, don't add "Client:" prefix to avoid confusion
        clientSession?.send(Frame.Text(message))
        println("Successfully sent to server")
    } catch (e: Exception) {
        println("Error sending to server: ${e.message}")
    }
    println("=== CLIENT SENDING COMPLETE ===")
}

// Function to broadcast deletion command from server to all clients
suspend fun broadcastDeletionToClients(messageId: String) {
    println("=== SERVER BROADCASTING DELETION TO CLIENTS ===")
    println("Message ID to delete: $messageId")
    println("Number of connected clients: ${serverClients.size}")
    
    val deadClients = mutableSetOf<DefaultWebSocketServerSession>()
    serverClients.forEach { client ->
        try {
            val deletionCommand = "DELETE:$messageId"
            client.send(Frame.Text(deletionCommand))
            println("Successfully sent deletion command to client")
        } catch (e: Exception) {
            println("Error sending deletion command to client: ${e.message}")
            deadClients.add(client)
        }
    }
    serverClients.removeAll(deadClients)
    println("=== SERVER DELETION BROADCAST COMPLETE ===")
}

// Function to send deletion command from client to server
suspend fun sendDeletionToServer(messageId: String) {
    println("=== CLIENT SENDING DELETION TO SERVER ===")
    println("Message ID to delete: $messageId")
    println("Client session exists: ${clientSession != null}")
    
    try {
        val deletionCommand = "DELETE:$messageId"
        clientSession?.send(Frame.Text(deletionCommand))
        println("Successfully sent deletion command to server")
    } catch (e: Exception) {
        println("Error sending deletion command to server: ${e.message}")
    }
    println("=== CLIENT DELETION SENDING COMPLETE ===")
}

// Function to handle received deletion command
fun handleReceivedDeletion(messageId: String) {
    println("=== HANDLING RECEIVED DELETION ===")
    println("Message ID to delete: $messageId")
    println("Current global messages count: ${globalMessages.size}")
    println("Available message IDs: ${globalMessages.map { it.id }}")
    
    // Find and remove the message from global messages
    val messageToDelete = globalMessages.find { it.id == messageId }
    if (messageToDelete != null) {
        println("Found message to delete: ${messageToDelete.content}")
        
        // Delete associated image file if it's an image message
        if (messageToDelete.isImage && messageToDelete.imageFileName != null) {
            globalStorageManager.deleteImage(messageToDelete.imageFileName)
        }
        
        // Remove from global messages
        globalMessages.removeAll { it.id == messageId }
        
        // Add a notification message that the message was removed
        val removalNotification = ChatMessage(
            id = "removal_${System.currentTimeMillis()}",
            sender = "system",
            content = "The Message is Removed!",
            isImage = false,
            timestamp = System.currentTimeMillis()
        )
        globalMessages.add(removalNotification)
        
        // Save updated messages to storage
        globalStorageManager.saveMessages(globalMessages.toList())
        
        // Notify UI of the removal notification
        onMessageReceived?.invoke(removalNotification)
        
        println("Message deleted successfully from other device")
        println("Remaining messages count: ${globalMessages.size}")
    } else {
        println("Message not found for deletion: $messageId")
        println("Available message IDs: ${globalMessages.map { it.id }}")
    }
    println("=== DELETION HANDLING COMPLETE ===")
}

// Helper function to get local IP address
fun getLocalIpAddress(context: Context): String? {
    return try {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val dhcp: DhcpInfo = wifi.dhcpInfo ?: return null
        val ip = dhcp.ipAddress
        if (ip == 0) return null
        val quads = ByteArray(4)
        for (k in 0..3) {
            quads[k] = (ip shr (k * 8) and 0xFF).toByte()
        }
        InetAddress.getByAddress(quads).hostAddress
    } catch (_: Throwable) { 
        null 
    }
}

// Working WebSocket server function
suspend fun startWebSocketServer(port: Int, onClientConnect: (() -> Unit)? = null) {
    withContext(Dispatchers.IO) {
        try {
            println("Creating WebSocket server on port $port")
            val connectedClients = mutableSetOf<DefaultWebSocketServerSession>()
            
            val server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                install(io.ktor.server.websocket.WebSockets) {
                    maxFrameSize = Long.MAX_VALUE
                }
                routing {
                    // Test endpoint to verify server is running
                    get("/test") {
                        call.respondText("Server is running on port $port")
                    }
                    
                    webSocket("/ws") {
                        println("Client connected to WebSocket server")
                        connectedClients.add(this)
                        serverClients.add(this) // Add to global list
                        
                        // Notify that client connected
                        onClientConnect?.invoke()
                        
                        try {
                            // Send a welcome message to the client
                            send(Frame.Text("Welcome to the chat server!"))
                            println("Welcome message sent to client")
                            
                            for (frame in incoming) {
                                when (frame) {
                                    is Frame.Text -> {
                                        val msg = frame.readText()
                                        println("Server received: $msg")
                                        
                                        // Create a unique ID for this received message
                                        val messageId = "recv_${System.currentTimeMillis()}_${msg.hashCode()}"
                                        
                                        // Only process if we haven't seen this message before and not from this server
                                        if (!receivedMessageIds.contains(messageId) && !msg.startsWith("Server: ")) {
                                            receivedMessageIds.add(messageId)
                                            
                                            // Set flag to prevent UI state corruption
                                            println("=== SERVER SETTING FLAG TO TRUE ===")
                                            isProcessingReceivedMessage = true
                                            
                                            // Handle deletion commands
                                            if (msg.startsWith("DELETE:")) {
                                                val messageIdToDelete = msg.substringAfter("DELETE:")
                                                println("=== SERVER RECEIVED DELETION COMMAND ===")
                                                println("Raw message: $msg")
                                                println("Message ID to delete: $messageIdToDelete")
                                                println("Current global messages before deletion: ${globalMessages.map { "${it.id}:${it.content}" }}")
                                                
                                                // Handle the deletion
                                                handleReceivedDeletion(messageIdToDelete)
                                            }
                                            // Handle image messages
                                            else if (msg.startsWith("IMAGE:")) {
                                                val afterImage = msg.substringAfter("IMAGE:")
                                                val parts = afterImage.split(":", limit = 3)
                                                val sender = parts[0]
                                                val originalMessageId = parts.getOrNull(1) ?: messageId
                                                val imageData = parts.getOrNull(2) ?: afterImage.substringAfter(":")
                                                
                                                println("=== SERVER RECEIVED IMAGE ===")
                                                println("Sender: $sender")
                                                println("Original message ID: $originalMessageId")
                                                println("Image data length: ${imageData.length}")
                                                println("Image data preview: ${imageData.take(50)}...")
                                                
                                                // Save image to storage with original ID
                                                val imageFileName = globalStorageManager.saveImage(imageData, originalMessageId)
                                                
                                                val chatMessage = ChatMessage(
                                                    id = originalMessageId,
                                                    sender = sender,
                                                    content = "[Image]",
                                                    isImage = true,
                                                    imageData = imageData,
                                                    imageFileName = imageFileName
                                                )
                                                globalMessages.add(chatMessage)
                                                
                                                // Save all messages to storage
                                                globalStorageManager.saveMessages(globalMessages.toList())
                                                
                                                onMessageReceived?.invoke(chatMessage)
                                            } else if (msg.startsWith("TEXT:")) {
                                                // Handle new text message format with ID
                                                val afterText = msg.substringAfter("TEXT:")
                                                val parts = afterText.split(":", limit = 3)
                                                val sender = parts[0]
                                                val originalMessageId = parts.getOrNull(1) ?: messageId
                                                val textContent = parts.getOrNull(2) ?: afterText.substringAfter(":")
                                                
                                                println("=== SERVER RECEIVED TEXT MESSAGE ===")
                                                println("Sender: $sender")
                                                println("Original Message ID: $originalMessageId")
                                                println("Text content: $textContent")
                                                
                                                val chatMessage = ChatMessage(
                                                    id = originalMessageId,
                                                    sender = sender.lowercase(),
                                                    content = textContent,
                                                    isImage = false
                                                )
                                                globalMessages.add(chatMessage)
                                                
                                                // Save all messages to storage
                                                globalStorageManager.saveMessages(globalMessages.toList())
                                                
                                                onMessageReceived?.invoke(chatMessage)
                                            } else {
                                                // Handle legacy text message format (fallback)
                                                val chatMessage = ChatMessage(
                                                    id = messageId,
                                                    sender = "client",
                                                    content = msg,
                                                    isImage = false
                                                )
                                                globalMessages.add(chatMessage)
                                                
                                                // Save all messages to storage
                                                globalStorageManager.saveMessages(globalMessages.toList())
                                                
                                                onMessageReceived?.invoke(chatMessage)
                                            }
                                            
                                            // Clear flag after processing with a small delay
                                            delay(100) // Small delay to ensure UI updates are complete
                                            println("=== SERVER SETTING FLAG TO FALSE ===")
                                            isProcessingReceivedMessage = false
                                            
                                            println("Server processed received message: $msg with ID: $messageId")
                                        } else {
                                            println("Server ignored duplicate message: $msg")
                                        }
                                        
                                        // Broadcast to all connected clients except sender
                                        val deadClients = mutableSetOf<DefaultWebSocketServerSession>()
                                        connectedClients.forEach { client ->
                                            try {
                                                if (client != this) {
                                                    client.send(Frame.Text(msg))
                                                }
                                            } catch (e: Exception) {
                                                println("Error sending to client: ${e.message}")
                                                deadClients.add(client)
                                            }
                                        }
                                        // Remove dead clients
                                        connectedClients.removeAll(deadClients)
                                        serverClients.removeAll(deadClients)
                                    }
                                    is Frame.Close -> {
                                        println("Client sent close frame")
                                        break
                                    }
                                    is Frame.Ping -> {
                                        println("Received ping from client")
                                        send(Frame.Pong(frame.buffer))
                                    }
                                    is Frame.Pong -> {
                                        println("Received pong from client")
                                    }
                                    else -> {
                                        println("Received unknown frame type: ${frame::class.simpleName}")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            println("WebSocket error: ${e.message}")
                            println("WebSocket error stack trace: ${e.stackTrace.joinToString("\n")}")
                        } finally {
                            connectedClients.remove(this)
                            serverClients.remove(this)
                            println("Client disconnected from WebSocket server")
                        }
                    }
                }
            }
            
            println("Starting server...")
            server.start(wait = false)
            
            // Wait a bit for server to be ready
            delay(500)
            println("WebSocket server started successfully on port $port")
            
        } catch (e: Exception) {
            println("Failed to start WebSocket server: ${e.message}")
            println("Stack trace: ${e.stackTrace.joinToString("\n")}")
            throw e
        }
    }
}

// Data class to represent chat messages
@kotlinx.serialization.Serializable
data class ChatMessage(
    val id: String,
    val sender: String,
    val content: String,
    val isImage: Boolean = false,
    val imageData: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val imageFileName: String? = null // For persistent storage
)

// Storage manager for persistent message storage
class MessageStorageManager(private val context: Context) {
    private val wcwiFolder = File(context.getExternalFilesDir(null), "WCWI")
    private val messagesFile = File(wcwiFolder, "messages.json")
    private val imagesFolder = File(wcwiFolder, "images")
    private val json = Json { prettyPrint = true }
    
    init {
        // Create WCWI folder if it doesn't exist
        if (!wcwiFolder.exists()) {
            wcwiFolder.mkdirs()
        }
        if (!imagesFolder.exists()) {
            imagesFolder.mkdirs()
        }
    }
    
    fun saveMessages(messages: List<ChatMessage>) {
        try {
            val jsonString = json.encodeToString(messages)
            messagesFile.writeText(jsonString)
            println("Messages saved to: ${messagesFile.absolutePath}")
        } catch (e: Exception) {
            println("Error saving messages: ${e.message}")
        }
    }
    
    fun loadMessages(): List<ChatMessage> {
        return try {
            if (messagesFile.exists()) {
                val jsonString = messagesFile.readText()
                json.decodeFromString<List<ChatMessage>>(jsonString)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            println("Error loading messages: ${e.message}")
            emptyList()
        }
    }
    
    fun saveImage(imageData: String, messageId: String): String {
        return try {
            val imageBytes = Base64.getDecoder().decode(imageData)
            val fileName = "image_${messageId}.jpg"
            val imageFile = File(imagesFolder, fileName)
            
            FileOutputStream(imageFile).use { fos ->
                fos.write(imageBytes)
            }
            
            println("Image saved to: ${imageFile.absolutePath}")
            fileName
        } catch (e: Exception) {
            println("Error saving image: ${e.message}")
            ""
        }
    }
    
    fun loadImage(fileName: String): String? {
        return try {
            val imageFile = File(imagesFolder, fileName)
            if (imageFile.exists()) {
                val imageBytes = imageFile.readBytes()
                Base64.getEncoder().encodeToString(imageBytes)
            } else {
                null
            }
        } catch (e: Exception) {
            println("Error loading image: ${e.message}")
            null
        }
    }
    
    fun getStoragePath(): String {
        return wcwiFolder.absolutePath
    }
    
    fun deleteImage(fileName: String): Boolean {
        return try {
            val imageFile = File(imagesFolder, fileName)
            if (imageFile.exists()) {
                val deleted = imageFile.delete()
                if (deleted) {
                    println("Image file deleted: ${imageFile.absolutePath}")
                } else {
                    println("Failed to delete image file: ${imageFile.absolutePath}")
                }
                deleted
            } else {
                println("Image file does not exist: ${imageFile.absolutePath}")
                true // Consider it deleted if it doesn't exist
            }
        } catch (e: Exception) {
            println("Error deleting image: ${e.message}")
            false
        }
    }
    
    fun deleteMessages(messagesToDelete: List<ChatMessage>): Boolean {
        return try {
            var allDeleted = true
            
            // Delete associated image files
            messagesToDelete.forEach { message ->
                if (message.isImage && message.imageFileName != null) {
                    val imageDeleted = deleteImage(message.imageFileName)
                    if (!imageDeleted) {
                        allDeleted = false
                    }
                }
            }
            
            println("Deleted ${messagesToDelete.size} messages and their associated files")
            allDeleted
        } catch (e: Exception) {
            println("Error deleting messages: ${e.message}")
            false
        }
    }
}

class MainActivity : ComponentActivity() {

    // Image picker launcher
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedImageUri ->
            // Handle the selected image
            handleSelectedImage(selectedImageUri)
        }
    }
    
    // Bluetooth components
    private lateinit var bluetoothManager: BluetoothManager
    private var bluetoothAdapter: BluetoothAdapter? = null
    
    // Storage manager for persistent data
    private lateinit var storageManager: MessageStorageManager
    
    // Permission request launcher
    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // Permissions granted, proceed with Bluetooth operations
            println("Bluetooth permissions granted")
        } else {
            // Permissions denied
            println("Bluetooth permissions denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        // Initialize storage manager
        storageManager = MessageStorageManager(this)
        globalStorageManager = storageManager
        
        // Load saved messages
        val savedMessages = storageManager.loadMessages()
        globalMessages.clear()
        globalMessages.addAll(savedMessages)
        
        // Load images for saved messages
        savedMessages.forEach { message ->
            if (message.isImage && message.imageFileName != null) {
                val imageData = storageManager.loadImage(message.imageFileName)
                if (imageData != null) {
                    // Update the message with loaded image data
                    val index = globalMessages.indexOfFirst { it.id == message.id }
                    if (index != -1) {
                        globalMessages[index] = message.copy(imageData = imageData)
                    }
                }
            }
        }
        
        println("Loaded ${savedMessages.size} saved messages from storage")
        println("Storage path: ${storageManager.getStoragePath()}")
        
        // Initialize Bluetooth components
        bluetoothManager = BluetoothManager(this)
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        
        // Set up Bluetooth message handling
        bluetoothManager.onMessageReceived = { message ->
            println("Received Bluetooth message: $message")
            val messageId = "bluetooth_${System.currentTimeMillis()}_${message.hashCode()}"
            
            // Handle deletion commands
            if (message.startsWith("DELETE:")) {
                val messageIdToDelete = message.substringAfter("DELETE:")
                println("=== BLUETOOTH RECEIVED DELETION COMMAND ===")
                println("Raw message: $message")
                println("Message ID to delete: $messageIdToDelete")
                println("Current global messages before deletion: ${globalMessages.map { "${it.id}:${it.content}" }}")
                
                // Handle the deletion
                handleReceivedDeletion(messageIdToDelete)
            }
            // Handle image messages
            else if (message.startsWith("IMAGE:")) {
                val afterImage = message.substringAfter("IMAGE:")
                val parts = afterImage.split(":", limit = 3)
                val sender = parts[0]
                val originalMessageId = parts.getOrNull(1) ?: messageId
                val imageData = parts.getOrNull(2) ?: afterImage.substringAfter(":")
                
                println("=== BLUETOOTH RECEIVED IMAGE ===")
                println("Sender: $sender")
                println("Original message ID: $originalMessageId")
                println("Image data length: ${imageData.length}")
                println("Image data preview: ${imageData.take(50)}...")
                
                // Save image to storage with original ID
                val imageFileName = storageManager.saveImage(imageData, originalMessageId)
                
                val chatMessage = ChatMessage(
                    id = originalMessageId,
                    sender = sender,
                    content = "[Image]",
                    isImage = true,
                    imageData = imageData,
                    imageFileName = imageFileName
                )
                globalMessages.add(chatMessage)
                
                // Save all messages to storage
                globalStorageManager.saveMessages(globalMessages.toList())
                
                onMessageReceived?.invoke(chatMessage)
            } else if (message.startsWith("TEXT:")) {
                // Handle new text message format with ID
                val afterText = message.substringAfter("TEXT:")
                val parts = afterText.split(":", limit = 3)
                val sender = parts[0]
                val originalMessageId = parts.getOrNull(1) ?: messageId
                val textContent = parts.getOrNull(2) ?: afterText.substringAfter(":")
                
                println("=== BLUETOOTH RECEIVED TEXT MESSAGE ===")
                println("Sender: $sender")
                println("Original Message ID: $originalMessageId")
                println("Text content: $textContent")
                
                val chatMessage = ChatMessage(
                    id = originalMessageId,
                    sender = sender.lowercase(),
                    content = textContent,
                    isImage = false
                )
                globalMessages.add(chatMessage)
                
                // Save all messages to storage
                globalStorageManager.saveMessages(globalMessages.toList())
                
                onMessageReceived?.invoke(chatMessage)
            } else {
                // Handle legacy text message format (fallback)
                val chatMessage = ChatMessage(
                    id = messageId,
                    sender = if (bluetoothManager.isServer()) "client" else "server",
                    content = message,
                    isImage = false
                )
                globalMessages.add(chatMessage)
                
                // Save all messages to storage
                globalStorageManager.saveMessages(globalMessages.toList())
                
                onMessageReceived?.invoke(chatMessage)
            }
        }
        
        // Bluetooth connection status callback will be set inside setContent
        
        bluetoothManager.onDeviceDiscovered = { device ->
            // Add discovered device to the list
            println("Bluetooth device discovered: ${device.name}")
        }

        setContent {
            // State management for connection
            var connected by remember { mutableStateOf(false) }
            var isServer by remember { mutableStateOf(false) }
            var statusMessage by remember { mutableStateOf("Choose connection mode") }
            var serverIp by remember { mutableStateOf("") }
            var showIpInput by remember { mutableStateOf(false) }
            // var clientConnected by remember { mutableStateOf(false) } // Removed unused variable
            val uiScope = rememberCoroutineScope()
            val serverPort = 9876
            
            // Bluetooth state variables
            var connectionMode by remember { mutableStateOf("") } // "wifi" or "bluetooth"
            var bluetoothDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
            var showBluetoothDevices by remember { mutableStateOf(false) }
            var bluetoothConnected by remember { mutableStateOf(false) }
            var isBluetoothServer by remember { mutableStateOf(false) }

            // Set up Bluetooth connection status callback to update UI
            LaunchedEffect(Unit) {
                bluetoothManager.onConnectionStatusChanged = { bluetoothConnected, status ->
                    println("Bluetooth connection status: $bluetoothConnected - $status")
                    if (bluetoothConnected) {
                        // Update UI state when Bluetooth connection is established
                        connected = true
                        if (isBluetoothServer) {
                            // Server mode - client connected
                            isServer = true
                            statusMessage = "Client connected - Chat ready"
                        } else {
                            // Client mode - connected to server
                            isServer = false
                            statusMessage = "Connected to server - Chat ready"
                        }
                        println("Bluetooth connection established - UI updated")
                    } else {
                        // Connection lost
                        connected = false
                        isServer = false
                        isBluetoothServer = false
                        statusMessage = "Bluetooth connection lost"
                        println("Bluetooth connection lost - UI updated")
                    }
                }
            }

                    if (connected) {
                        // Simple chat interface instead of complex ChatScreen
                        SimpleChatScreen(
                            isServer = isServer,
                            serverIp = if (isServer) getLocalIpAddress(applicationContext) ?: "Unknown" else serverIp,
                            serverPort = serverPort,
                            connectionMode = connectionMode,
                            bluetoothManager = bluetoothManager,
                            storageManager = storageManager,
                            onSendMessage = { message ->
                                // Handle message sending
                                println("=== onSendMessage CALLED ===")
                                println("Message: $message")
                                println("Is Server: $isServer")
                                println("Is processing received message: $isProcessingReceivedMessage")
                                println("Current time: ${System.currentTimeMillis()}")
                                println("Thread: ${Thread.currentThread().name}")
                                println("Connected: $connected")
                                println("Status message: $statusMessage")
                                println("Stack trace: ${Thread.currentThread().stackTrace.take(5).joinToString("\n")}")
                                
                                // Prevent sending when processing received messages
                                if (!isProcessingReceivedMessage) {
                                    if (connectionMode == "bluetooth") {
                                        // For Bluetooth, send directly through bluetoothManager
                                        println("Sending Bluetooth message: $message")
                                        val success = bluetoothManager.sendMessage(message)
                                        println("Bluetooth send result: $success")
                                    } else {
                                        // For WiFi, use the WebSocket methods
                                uiScope.launch(Dispatchers.IO) {
                                    if (isServer) {
                                        // Server sends to all connected clients
                                        sendMessageToClients(message)
                                    } else {
                                        // Client sends to server
                                        sendMessageToServer(message)
                                    }
                                }
                                    }
                                } else {
                                    println("=== onSendMessage IGNORED - Processing received message ===")
                                }
                            },
                            onImageClick = {
                                imagePickerLauncher.launch("image/*")
                            }
                        )
                    } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Wireless Communication Without Internet",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "(WCWI)",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontSize = 19.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    // Connection mode selection
                    if (connectionMode.isEmpty()) {
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = { 
                                    connectionMode = "wifi"
                                    statusMessage = "Choose WiFi connection mode"
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("WiFi")
                            }
                            
                            Button(
                                onClick = { 
                                    connectionMode = "bluetooth"
                                    statusMessage = "Choose Bluetooth connection mode"
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Bluetooth")
                            }
                        }
                    }
                    
                    // WiFi connection options
                    if (connectionMode == "wifi" && showIpInput) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TextField(
                                value = serverIp,
                                onValueChange = { serverIp = it },
                                label = { Text("Enter server IP (e.g., 192.168.1.100)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    if (serverIp.isNotBlank()) {
                                        uiScope.launch(Dispatchers.IO) {
                                            try {
                                                withContext(Dispatchers.Main) { 
                                                    statusMessage = "Connecting to $serverIp..." 
                                                }
                                                println("=== CLIENT CONNECTION BEGIN ===")
                                                println("Attempting to connect to $serverIp:$serverPort")
                                                
                                                // First test HTTP connection
                                                val testUrl = "http://$serverIp:$serverPort/test"
                                                val response = java.net.URL(testUrl).openConnection()
                                                response.connectTimeout = 5000
                                                response.readTimeout = 5000
                                                val inputStream = response.getInputStream()
                                                val responseText = inputStream.bufferedReader().use { it.readText() }
                                                println("Server test response: $responseText")
                                                
                                                // Now establish WebSocket connection
                                                val client = platformHttpClient()
                                                val wsSession = client.webSocketSession(host = serverIp, port = serverPort, path = "/ws")
                                                clientSession = wsSession // Store globally
                                                println("WebSocket session established")
                                                
                                                // Send a connection message to notify server
                                                wsSession.send(Frame.Text("Client connected"))
                                                println("Sent connection message to server")
                                                
                                                // Start listening for incoming messages
                                                uiScope.launch(Dispatchers.IO) {
                                                    try {
                                                        for (frame in wsSession.incoming) {
                                                            if (frame is Frame.Text) {
                                                                val msg = frame.readText()
                                                                println("Client received: $msg")
                                                                
                                                                // Create a unique ID for this received message
                                                                val messageId = "recv_${System.currentTimeMillis()}_${msg.hashCode()}"
                                                                
                                                                // Only process if we haven't seen this message before
                                                                if (!receivedMessageIds.contains(messageId) && !msg.startsWith("Client: ")) {
                                                                    receivedMessageIds.add(messageId)
                                                                    
                                                                    // Set flag to prevent UI state corruption
                                                                    println("=== CLIENT SETTING FLAG TO TRUE ===")
                                                                    isProcessingReceivedMessage = true
                                                                    
                                                                    withContext(Dispatchers.Main) {
                                                                        // Handle deletion commands
                                                                        if (msg.startsWith("DELETE:")) {
                                                                            val messageIdToDelete = msg.substringAfter("DELETE:")
                                                                            println("=== CLIENT RECEIVED DELETION COMMAND ===")
                                                                            println("Raw message: $msg")
                                                                            println("Message ID to delete: $messageIdToDelete")
                                                                            println("Current global messages before deletion: ${globalMessages.map { "${it.id}:${it.content}" }}")
                                                                            
                                                                            // Handle the deletion
                                                                            handleReceivedDeletion(messageIdToDelete)
                                                                        }
                                                                        // Handle image messages
                                                                        else if (msg.startsWith("IMAGE:")) {
                                                                            val afterImage = msg.substringAfter("IMAGE:")
                                                                            val parts = afterImage.split(":", limit = 3)
                                                                            val sender = parts[0]
                                                                            val originalMessageId = parts.getOrNull(1) ?: messageId
                                                                            val imageData = parts.getOrNull(2) ?: afterImage.substringAfter(":")
                                                                            
                                                                            println("=== CLIENT RECEIVED IMAGE ===")
                                                                            println("Sender: $sender")
                                                                            println("Original message ID: $originalMessageId")
                                                                            println("Image data length: ${imageData.length}")
                                                                            println("Image data preview: ${imageData.take(50)}...")
                                                                            
                                                                            // Save image to storage with original ID
                                                                            val imageFileName = storageManager.saveImage(imageData, originalMessageId)
                                                                            
                                                                            val chatMessage = ChatMessage(
                                                                                id = originalMessageId,
                                                                                sender = sender,
                                                                                content = "[Image]",
                                                                                isImage = true,
                                                                                imageData = imageData,
                                                                                imageFileName = imageFileName
                                                                            )
                                                                            globalMessages.add(chatMessage)
                                                                            
                                                                            // Save all messages to storage
                                                                            globalStorageManager.saveMessages(globalMessages.toList())
                                                                            
                                                                            onMessageReceived?.invoke(chatMessage)
                                                                        } else if (msg.startsWith("TEXT:")) {
                                                                            // Handle new text message format with ID
                                                                            val afterText = msg.substringAfter("TEXT:")
                                                                            val parts = afterText.split(":", limit = 3)
                                                                            val sender = parts[0]
                                                                            val originalMessageId = parts.getOrNull(1) ?: messageId
                                                                            val textContent = parts.getOrNull(2) ?: afterText.substringAfter(":")
                                                                            
                                                                            println("=== CLIENT RECEIVED TEXT MESSAGE ===")
                                                                            println("Sender: $sender")
                                                                            println("Original Message ID: $originalMessageId")
                                                                            println("Text content: $textContent")
                                                                            
                                                                            val chatMessage = ChatMessage(
                                                                                id = originalMessageId,
                                                                                sender = sender.lowercase(),
                                                                                content = textContent,
                                                                                isImage = false
                                                                            )
                                                                            globalMessages.add(chatMessage)
                                                                            
                                                                            // Save all messages to storage
                                                                            globalStorageManager.saveMessages(globalMessages.toList())
                                                                            
                                                                            onMessageReceived?.invoke(chatMessage)
                                                                        } else {
                                                                            // Handle legacy text message format (fallback)
                                                                            val chatMessage = ChatMessage(
                                                                                id = messageId,
                                                                                sender = "server",
                                                                                content = msg,
                                                                                isImage = false
                                                                            )
                                                                            globalMessages.add(chatMessage)
                                                                            
                                                                            // Save all messages to storage
                                                                            globalStorageManager.saveMessages(globalMessages.toList())
                                                                            
                                                                            onMessageReceived?.invoke(chatMessage)
                                                                        }
                                                                    }
                                                                    
                                                                    // Clear flag after processing with a small delay
                                                                    delay(100) // Small delay to ensure UI updates are complete
                                                                    println("=== CLIENT SETTING FLAG TO FALSE ===")
                                                                    isProcessingReceivedMessage = false
                                                                    println("Client processed received message: $msg with ID: $messageId")
                                                                } else {
                                                                    println("Client ignored duplicate message: $msg")
                                                                }
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        println("Error receiving messages: ${e.message}")
                                                        isProcessingReceivedMessage = false
                                                    }
                                                }
                                                
                                                withContext(Dispatchers.Main) { 
                                                    connected = true
                                                    isServer = false
                                                    statusMessage = "Connected to $serverIp"
                                                }
                                                println("Successfully connected to $serverIp:$serverPort")
                                                println("=== CLIENT CONNECTION COMPLETE ===")
                                                
                                            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                                                println("=== CLIENT CONNECTION TIMEOUT ===")
                                                withContext(Dispatchers.Main) { 
                                                    statusMessage = "Connection timeout - make sure server is running"
                                                }
                                            } catch (e: Exception) {
                                                println("=== CLIENT CONNECTION ERROR ===")
                                                println("Connection error: ${e.message}")
                                                println("Stack trace: ${e.stackTrace.joinToString("\n")}")
                                                withContext(Dispatchers.Main) { 
                                                    statusMessage = "Connection failed: ${e.message}"
                                                }
                                                println("=== CLIENT CONNECTION ERROR END ===")
                                            }
                                        }
                                    }
                                }) { Text("Connect") }
                                Button(onClick = { 
                                    showIpInput = false
                                    statusMessage = "Choose connection mode"
                                }) { Text("Cancel") }
                            }
                        }
                    } else if (connectionMode == "wifi") {
                        // WiFi Server button
                        Button(
                            onClick = {
                                uiScope.launch(Dispatchers.IO) {
                                    try {
                                        withContext(Dispatchers.Main) { 
                                            statusMessage = "Starting server..." 
                                        }
                                        println("=== SERVER STARTUP BEGIN ===")
                                        println("Starting WebSocket server on port $serverPort")
                                        println("Current isServer value before startup: $isServer")
                                        
                                        // Set server mode BEFORE starting the server (since startWebSocketServer never returns)
                                        println("=== SETTING SERVER MODE ===")
                                        println("About to set isServer = true")
                                        isServer = true
                                        println("isServer set to: $isServer")
                                        println("Verification - isServer is now: $isServer")
                                        
                                        // Get IP address
                                        val myIp = getLocalIpAddress(applicationContext) ?: "Unknown"
                                        println("Server IP address: $myIp")
                                        
                                        // Update UI BEFORE starting server
                                        println("=== ENTERING withContext(Dispatchers.Main) ===")
                                        withContext(Dispatchers.Main) {
                                            println("=== INSIDE withContext(Dispatchers.Main) ===")
                                            println("isServer value inside withContext: $isServer")
                                            statusMessage = "Server running on $myIp:$serverPort - Waiting for client..."
                                            println("Status message set to: $statusMessage")
                                        }
                                        println("=== EXITED withContext(Dispatchers.Main) ===")
                                        
                                        // Start the server (this will run indefinitely and never return)
                                        println("=== CALLING startWebSocketServer ===")
                                        startWebSocketServer(serverPort) { 
                                            // Callback when client connects
                                            println("=== CLIENT CONNECTION CALLBACK ===")
                                            connected = true
                                            statusMessage = "Client connected - Chat ready"
                                        }
                                        // This line will never be reached because startWebSocketServer never returns
                                        println("=== startWebSocketServer CALL COMPLETED ===")
                                        
                                    } catch (e: Exception) {
                                        println("=== SERVER STARTUP ERROR ===")
                                        println("Server startup error: ${e.message}")
                                        println("Stack trace: ${e.stackTrace.joinToString("\n")}")
                                        withContext(Dispatchers.Main) { 
                                            statusMessage = "Server failed: ${e.message}"
                                        }
                                        println("=== SERVER STARTUP ERROR END ===")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start Server")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Client button
                        Button(
                            onClick = {
                                showIpInput = true
                                statusMessage = "Enter server IP address"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Connect as Client")
                        }
                    }
                    
                    // Bluetooth connection options
                    if (connectionMode == "bluetooth") {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Bluetooth Server button
                            Button(
                                onClick = {
                                    if (checkBluetoothPermissions()) {
                                uiScope.launch(Dispatchers.IO) {
                                    try {
                                        withContext(Dispatchers.Main) { 
                                                    statusMessage = "Starting Bluetooth server..." 
                                                }
                                                
                                                if (bluetoothManager.startServer()) {
                                        withContext(Dispatchers.Main) { 
                                                        isBluetoothServer = true
                                                        isServer = true  // Set the main isServer flag for Bluetooth server
                                                        statusMessage = "Bluetooth server running - Waiting for client..."
                                                    }
                                                } else {
                                                    withContext(Dispatchers.Main) {
                                                        statusMessage = "Failed to start Bluetooth server"
                                                    }
                                                }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) { 
                                                    statusMessage = "Bluetooth server error: ${e.message}"
                                                }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                                Text("Start Bluetooth Server")
                        }

                            Spacer(modifier = Modifier.height(8.dp))
                        
                            // Bluetooth Client button
                        Button(
                            onClick = { 
                                    if (checkBluetoothPermissions()) {
                                        showBluetoothDevices = true
                                        statusMessage = "Scanning for Bluetooth devices..."
                                        
                                        // Load paired devices
                                        bluetoothDevices = bluetoothManager.getPairedDevices()
                                        
                                        // Start discovery
                                        bluetoothManager.startDiscovery()
                                    }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                                Text("Connect as Bluetooth Client")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = if (connectionMode == "wifi") {
                            "WiFi Instructions:\n1. One device: Start Server\n2. Other device: Enter server IP and connect"
                        } else if (connectionMode == "bluetooth") {
                            "Bluetooth Instructions:\n1. One device: Start Bluetooth Server\n2. Other device: Connect as Client\n3. Make sure devices are paired"
                        } else {
                            "Instructions:\n1. Choose connection type (WiFi or Bluetooth)\n2. Follow the specific instructions for your choice"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    
                    // Back button to change connection mode
                    if (connectionMode.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { 
                                connectionMode = ""
                                statusMessage = "Choose connection mode"
                                showIpInput = false
                                showBluetoothDevices = false
                            }
                        ) {
                            Text("Back to Connection Type")
                        }
                    }
                }
                
                // Bluetooth device selection dialog
                if (showBluetoothDevices) {
                    AlertDialog(
                        onDismissRequest = { 
                            showBluetoothDevices = false
                            bluetoothManager.stopDiscovery()
                        },
                        title = { Text("Select Bluetooth Device") },
                        text = {
                            LazyColumn {
                                items(bluetoothDevices) { device ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable {
                                                uiScope.launch(Dispatchers.IO) {
                                                    try {
                                                        withContext(Dispatchers.Main) {
                                                            statusMessage = "Connecting to ${device.name}..."
                                                        }
                                                        
                                                        if (bluetoothManager.connectToDevice(device)) {
                                                            withContext(Dispatchers.Main) {
                                                                bluetoothConnected = true
                                                                connected = true
                                                                isServer = false  // Ensure client mode
                                                                isBluetoothServer = false  // Ensure not server mode
                                                                showBluetoothDevices = false
                                                                statusMessage = "Connected to ${device.name}"
                                                            }
                                                        } else {
                                                            withContext(Dispatchers.Main) {
                                                                statusMessage = "Failed to connect to ${device.name}"
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        withContext(Dispatchers.Main) {
                                                            statusMessage = "Connection error: ${e.message}"
                                                        }
                                                    }
                                                }
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Text(
                                            text = "${device.name ?: "Unknown Device"}\n${device.address}",
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = { 
                                    showBluetoothDevices = false
                                    bluetoothManager.stopDiscovery()
                                }
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }
    
    // Function to check and request Bluetooth permissions
    private fun checkBluetoothPermissions(): Boolean {
        val requiredPermissions = bluetoothManager.getRequiredPermissions()
        val missingPermissions = requiredPermissions.filter { permission ->
            ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        return if (missingPermissions.isNotEmpty()) {
            bluetoothPermissionLauncher.launch(missingPermissions.toTypedArray())
            false
        } else {
            true
        }
    }

    // Handle selected image
    private fun handleSelectedImage(uri: Uri) {
        try {
            println("=== IMAGE SELECTION DEBUG ===")
            println("Selected URI: $uri")
            
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            if (bitmap == null) {
                println("Failed to decode bitmap from URI")
                return
            }
            
            println("Original bitmap dimensions: ${bitmap.width}x${bitmap.height}")
            
            // Convert bitmap to base64 string
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, outputStream)
            val imageBytes = outputStream.toByteArray()
            val base64Image = Base64.getEncoder().encodeToString(imageBytes)
            
            println("Compressed image bytes: ${imageBytes.size}")
            println("Base64 string length: ${base64Image.length}")
            println("Base64 preview: ${base64Image.take(50)}...")
            
            // Send the image
            sendImageMessage(base64Image)
            
        } catch (e: Exception) {
            println("Error handling selected image: ${e.message}")
            e.printStackTrace()
        }
    }

    // Send image message
    private fun sendImageMessage(base64Image: String) {
        val uiScope = CoroutineScope(Dispatchers.Main)
        uiScope.launch(Dispatchers.IO) {
            try {
                // Determine connection mode and server/client status
                val isBluetoothMode = bluetoothManager.isConnected()
                val isServerMode = if (isBluetoothMode) {
                    bluetoothManager.isServer()
                } else {
                    serverClients.isNotEmpty() || clientSession == null
                }
                
                val messageId = "image_${System.currentTimeMillis()}_${if (isServerMode) "server" else "client"}"
                val imageMessage = "IMAGE:${if (isServerMode) "Server" else "Client"}:$messageId:$base64Image"
                
                // Add to sent messages tracking
                sentMessageIds.add(messageId)
                
                // Save image to storage
                val imageFileName = storageManager.saveImage(base64Image, messageId)
                
                // Add to display
                val chatMessage = ChatMessage(
                    id = messageId,
                    sender = if (isServerMode) "server" else "client",
                    content = "[Image]",
                    isImage = true,
                    imageData = base64Image,
                    imageFileName = imageFileName
                )
                globalMessages.add(chatMessage)
                
                // Save all messages to storage
                globalStorageManager.saveMessages(globalMessages.toList())
                
                // Trigger UI update immediately
                withContext(Dispatchers.Main) {
                    onMessageReceived?.invoke(chatMessage)
                }
                
                // Send the image to other device based on connection mode
                if (isBluetoothMode) {
                    // Send via Bluetooth
                    println("=== BLUETOOTH IMAGE SENDING ===")
                    println("Image message: $imageMessage")
                    val success = bluetoothManager.sendMessage(imageMessage)
                    if (success) {
                        println("Successfully sent image via Bluetooth")
                    } else {
                        println("Failed to send image via Bluetooth")
                    }
                } else {
                    // Send via WiFi WebSocket
                    if (isServerMode) {
                        sendImageToClients(imageMessage)
                    } else {
                        sendImageToServer(imageMessage)
                    }
                }
                
                println("Sending image message with ID: $messageId")
                
            } catch (e: Exception) {
                println("Error sending image: ${e.message}")
            }
        }
    }
}

// Simple chat screen that doesn't use the complex transport system
@OptIn(ExperimentalMaterial3Api::class)
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {


        }
    }
}
private var globalMessages = mutableListOf<String>()
private var onMessageReceived: ((String) -> Unit)? = null
@Composable
fun SimpleChatScreen(
    isServer: Boolean,
    serverIp: String,
    serverPort: Int,
    connectionMode: String,
    bluetoothManager: BluetoothManager,
    storageManager: MessageStorageManager,
    onSendMessage: (String) -> Unit,
    onImageClick: () -> Unit
) {
    println("=== SimpleChatScreen COMPOSED ===")
    println("SimpleChatScreen isServer parameter: $isServer")
    println("SimpleChatScreen serverIp: $serverIp")
    println("SimpleChatScreen serverPort: $serverPort")
    
    var messages by remember { mutableStateOf(globalMessages.toList()) }
    var inputText by remember { mutableStateOf("") }
    var fullscreenImage by remember { mutableStateOf<String?>(null) }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val uiScope = rememberCoroutineScope()
    
    // Selection state for delete functionality
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedMessages by remember { mutableStateOf(setOf<String>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    
    // Set up message reception callback to update UI
    LaunchedEffect(isServer) {
        onMessageReceived = { newMessage ->
            // Filter out system refresh messages
            if (newMessage.sender != "system" || newMessage.content != "REFRESH_UI") {
                // Update the UI when a new message is received
                messages = globalMessages.toList()
                println("UI updated with new message: $newMessage")
            } else {
                // This is a refresh message, just update the UI without adding the message
                messages = globalMessages.toList()
                println("UI refreshed due to message deletion")
            }
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Background image
        Image(
            painter = painterResource(R.drawable.wcwi),
            contentDescription = "Background",
            modifier = Modifier
                .fillMaxSize(),
            contentScale = ContentScale.Fit,
            //alpha = 0.5F
        )
        
        // Main content
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
            // Top App Bar with three-dot menu
            TopAppBar(
                title = {
        Text(
            text = if (connectionMode == "bluetooth") {
                if (isServer) "Bluetooth Server Mode" else "Bluetooth Client Mode"
            } else {
                if (isServer) "WiFi Server Mode" else "WiFi Client Mode" // IP: $serverIp:$serverPort
            },
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                actions = {
                    if (isSelectionMode) {
                        // Show cancel and delete buttons when in selection mode
                        IconButton(
                            onClick = {
                                isSelectionMode = false
                                selectedMessages = emptySet()
                            }
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Cancel selection"
                            )
                        }
                        
                        IconButton(
                            onClick = {
                                if (selectedMessages.isNotEmpty()) {
                                    showDeleteDialog = true
                                }
                            },
                            enabled = selectedMessages.isNotEmpty()
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete selected messages",
                                tint = if (selectedMessages.isNotEmpty()) Color.Red else Color.Gray
                            )
                        }
                    } else {
                        // Three-dot menu button
                        IconButton(
                            onClick = { showMenu = true }
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu",
                            )
                        }
                        
                        // Dropdown menu
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Delete Messages") },
                                onClick = {
                                    isSelectionMode = true
                                    showMenu = false
                                },
                                leadingIcon = {
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete"
                                    )
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(red = 255, green = 255 , blue = 255,alpha = 1),
                    titleContentColor = Color.Black
                ),
                modifier = Modifier.height(70.dp)
            )
            
            // Chat content with padding
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp) //16
            ) {
        
        // Messages display
        val listState = rememberLazyListState()
        
        // Auto-scroll to bottom when new messages arrive
        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
        
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
                 //reverseLayout = true

        ) {
            items(messages) { message ->
                // Determine if message is sent by current user
                // Server device: only "server" messages are sent by me
                // Client device: only "client" messages are sent by me
                val isSentByMe = if (isServer) {
                    message.sender == "server" || message.sender == "Server"
                } else {
                    message.sender == "client" || message.sender == "Client"
                }
                
                val isSelected = selectedMessages.contains(message.id)
                val isSystemMessage = message.sender == "system"
                
                // System messages (like "The Message is Removed!") should be displayed differently
                if (isSystemMessage) {
                    // Center-aligned system message
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Red,
                            fontStyle = FontStyle.Italic,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                } else {
                    //Whatsapp like bubble-style message
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clickable(
                            enabled = isSelectionMode
                        ) {
                            if (isSelectionMode) {
                                selectedMessages = if (isSelected) {
                                    selectedMessages - message.id
                                } else {
                                    selectedMessages + message.id
                                }
                            }
                        },
                    horizontalArrangement = if (isSentByMe) Arrangement.End else Arrangement.Start
                ) {
                    // Selection checkbox (only show in selection mode)
                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = {
                                selectedMessages = if (isSelected) {
                                    selectedMessages - message.id
                                } else {
                                    selectedMessages + message.id
                                }
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(horizontal = 4.dp)
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = Color.Unspecified,
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isSentByMe) 16.dp else 4.dp,
                                    bottomEnd = if (isSentByMe) 4.dp else 16.dp
                                )
                            ),
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isSentByMe) 16.dp else 4.dp,
                            bottomEnd = if (isSentByMe) 4.dp else 16.dp
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                if (isSentByMe) Color(
                                    red = 200,
                                    green = 220,
                                    blue = 255,
                                    alpha = 150,
                                ) else Color(
                                    red = 200,
                                    green = 255,
                                    blue = 200,
                                    alpha = 150
                                )
                            } else {
                                if (isSentByMe) Color(red=202, green=204, blue=33, alpha=150) else Color(red=220,green=248,blue=198, alpha = 150)
                            }
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            // Date at top right
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = java.text.SimpleDateFormat("dd-MM-yyyy")
                                        .format(java.util.Date(message.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF8B2999)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Message content
                            if (message.isImage && message.imageData != null) {
                                // Image message
                                Column {
                                    // Display image
                                    val imageBytes = try {
                                        val decoded = Base64.getDecoder().decode(message.imageData)
                                        decoded
                                    } catch (e: Exception) {
                                        null
                                    }
                                    
                                    val bitmap = imageBytes?.let { 
                                        BitmapFactory.decodeByteArray(it, 0, it.size)
                                    }
                                    
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Received Image",
                                            modifier = Modifier
                                                .width(200.dp)
                                                .height(200.dp)
                                                .clickable {
                                                    fullscreenImage = message.imageData
                                                    // Reset zoom state when opening image
                                                    scale = 1f
                                                    offsetX = 0f
                                                    offsetY = 0f
                                                }
                                                .alpha(1f),
                                            contentScale = ContentScale.FillWidth,
                                        )
                                    } else {
                                        Text(
                                            text = "Failed to load image",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.Red,
                                        )
                                    }
                                }
                            } else {
                                // Text message
                                Text(
                                    text = message.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Black,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.W400,
                                    fontSize = 17.sp
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Time at bottom right
        Row(
            modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = java.text.SimpleDateFormat("hh:mm:ss a")
                                        .format(java.util.Date(message.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF8B2999)
                                )
                            }
                        }
                    }
                }
                }
    onSendMessage: (String) -> Unit
) {
    var messages by remember { mutableStateOf(globalMessages.toList()) }
    var inputText by remember { mutableStateOf("") }
    val uiScope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        onMessageReceived = { newMessage ->
            globalMessages.add(newMessage)
            messages = globalMessages.toList()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(messages) { message ->
                Text(
                    text = message,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
        }
        
        // Input area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 35.dp), //15
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Type message...", color = Color.White)},
                modifier = Modifier
                    .weight(1f),
                    //.height(15.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(red = 171 , green = 96 , blue = 10 , alpha = 90), //0xFFab600a80
                    unfocusedContainerColor = Color(red = 171, green = 96 ,  blue = 10 , alpha = 90),
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                ),
                textStyle = TextStyle(
                    fontSize = 18.sp,
                    color = Color.White
                ),
                    //.height(40.dp),
                  //shape = TextFieldDefaults.MinHeight
                //colors = TextFieldDefaults.colors(0xFFab600a80)
            )
            Spacer(modifier = Modifier.width(8.dp))
            
            Button(
                onClick = {
                        // Prevent button click when processing received messages
                        println("=== BUTTON CLICK CHECK ===")
                        println("isProcessingReceivedMessage: $isProcessingReceivedMessage")
                        if (isProcessingReceivedMessage) {
                            println("=== BUTTON CLICK IGNORED - Processing received message ===")
                            return@Button
                        }
                        
                    if (inputText.isNotBlank()) {
                            println("=== BUTTON CLICKED ===")
                            println("Input text: $inputText")
                            println("Is Server: $isServer")
                            println("Is processing received message: $isProcessingReceivedMessage")
                            
                            val messageId = "sent_${System.currentTimeMillis()}_${if (isServer) "server" else "client"}"
                            
                            // Add to sent messages tracking
                            sentMessageIds.add(messageId)
                            
                            // Add to display
                            val chatMessage = ChatMessage(
                                id = messageId,
                                sender = if (isServer) "server" else "client",
                                content = inputText,
                                isImage = false
                            )
                            globalMessages.add(chatMessage)
                              messages = globalMessages.toList()
                            
                            // Save all messages to storage
                              globalStorageManager.saveMessages(globalMessages.toList())
                            
                            // Send the message to other device with message ID
                            onSendMessage("TEXT:${if (isServer) "Server" else "Client"}:$messageId:$inputText")
                            inputText = ""
                            println("Sending message: ${chatMessage.sender}: ${chatMessage.content} with ID: $messageId")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A3631))
            ) {
                Text("Send")
            }

            Spacer(modifier = Modifier.width(8.dp))
            // Image button
            Button(
                onClick = {
                    println("=== IMAGE BUTTON CLICKED ===")
                    onImageClick()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A3631))
            ) {
                Text("Image")
            }
        }
        
        // Fullscreen image dialog
        fullscreenImage?.let { imageData ->
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { fullscreenImage = null },
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .padding(0.dp)
                ) {
                    // Fullscreen image
                    val bitmap = try {
                        val imageBytes = Base64.getDecoder().decode(imageData)
                        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    } catch (e: Exception) {
                        null
                    }
                    
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Fullscreen Image",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                )
                                .pointerInput(Unit) {
                                    detectTransformGestures { centroid, pan, zoom, rotation ->
                                        val newScale = (scale * zoom).coerceIn(0.5f, 5f)
                                        val scaleChange = newScale / scale
                                        
                                        // Calculate the zoom center relative to the image center
                                        val imageCenterX = size.width / 2f
                                        val imageCenterY = size.height / 2f
                                        
                                        // Only allow panning when zoomed in (scale > 1.0)
                                        val panX = if (newScale > 1.0f) pan.x else 0f
                                        val panY = if (newScale > 1.0f) pan.y else 0f
                                        
                                        // Adjust translation to zoom from the touch point
                                        offsetX = imageCenterX + (offsetX - imageCenterX) * scaleChange + panX
                                        offsetY = imageCenterY + (offsetY - imageCenterY) * scaleChange + panY
                                        
                                        scale = newScale
                                        
                                        // Reset offset to center when zoomed out
                                        if (newScale <= 1.0f) {
                                            offsetX = 0f
                                            offsetY = 0f
                                        }
                                    }
                                }
                                .clickable { 
                                    fullscreenImage = null
                                    // Reset zoom when closing
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                },
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            text = "Failed to load image",
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
        
        // Delete confirmation dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Messages") },
                text = {
                    Text("Are you sure you want to delete ${selectedMessages.size} selected message(s)? This action cannot be undone.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            // Get messages to delete before removing from list
                            val messagesToDelete = globalMessages.filter { it.id in selectedMessages }
                            
                            // Check which messages are sent by current user vs received
                            val messagesToDeleteFromBoth = mutableListOf<ChatMessage>()
                            val messagesToDeleteFromOwn = mutableListOf<ChatMessage>()
                            
                            messagesToDelete.forEach { message ->
                                // Determine if current user is the sender of this message
                                val isSentByCurrentUser = if (isServer) {
                                    message.sender == "server" || message.sender == "Server"
                                } else {
                                    message.sender == "client" || message.sender == "Client"
                                }
                                
                                if (isSentByCurrentUser) {
                                    // Sender can delete from both screens
                                    messagesToDeleteFromBoth.add(message)
                                    println("=== SENDER DELETING MESSAGE FROM BOTH SCREENS ===")
                                    println("Message ID: ${message.id}")
                                    println("Message content: ${message.content}")
                                } else {
                                    // Receiver can only delete from own screen
                                    messagesToDeleteFromOwn.add(message)
                                    println("=== RECEIVER DELETING MESSAGE FROM OWN SCREEN ONLY ===")
                                    println("Message ID: ${message.id}")
                                    println("Message content: ${message.content}")
                                }
                            }
                            
                            // Broadcast deletion to other device only for messages sent by current user
                            if (messagesToDeleteFromBoth.isNotEmpty()) {
                                uiScope.launch(Dispatchers.IO) {
                                    messagesToDeleteFromBoth.forEach { message ->
                                        println("=== BROADCASTING DELETION FOR SENDER MESSAGE ===")
                                        println("Message ID: ${message.id}")
                                        println("Message content: ${message.content}")
                                        println("Message sender: ${message.sender}")
                                        
                                        // Determine connection mode and server/client status
                                        val isBluetoothMode = bluetoothManager.isConnected()
                                        val isServerMode = if (isBluetoothMode) {
                                            bluetoothManager.isServer()
                                        } else {
                                            serverClients.isNotEmpty() || clientSession == null
                                        }
                                        
                                        println("Connection mode: ${if (isBluetoothMode) "Bluetooth" else "WiFi"}")
                                        println("Device role: ${if (isServerMode) "Server" else "Client"}")
                                        
                                        // Send deletion command to other device
                                        if (isBluetoothMode) {
                                            // Bluetooth mode - send via Bluetooth
                                            println("Sending Bluetooth deletion command: DELETE:${message.id}")
                                            val success = bluetoothManager.sendMessage("DELETE:${message.id}")
                                            println("Bluetooth deletion send result: $success")
                                        } else {
                                            // WiFi mode - send via WebSocket
                                            if (isServerMode) {
                                                // Server broadcasting to clients
                                                println("Server broadcasting deletion to clients")
                                                broadcastDeletionToClients(message.id)
                                            } else {
                                                // Client sending to server
                                                println("Client sending deletion to server")
                                                sendDeletionToServer(message.id)
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // For messages deleted from own screen only, add local deletion notification
                            if (messagesToDeleteFromOwn.isNotEmpty()) {
                                messagesToDeleteFromOwn.forEach { message ->
                                    // Add local deletion notification
                                    val localRemovalNotification = ChatMessage(
                                        id = "local_removal_${System.currentTimeMillis()}",
                                        sender = "system",
                                        content = "The Message is Removed!",
                                        isImage = false,
                                        timestamp = System.currentTimeMillis()
                                    )
                                    globalMessages.add(localRemovalNotification)
                                    println("Added local deletion notification for message: ${message.id}")
                                }
                            }
                            
                            // Delete from storage (including image files)
                            globalStorageManager.deleteMessages(messagesToDelete)
                            
                            // Remove from in-memory list
                            globalMessages.removeAll { it.id in selectedMessages }
                            messages = globalMessages.toList()
                            
                            // Save updated messages to storage
                            globalStorageManager.saveMessages(globalMessages.toList())
                            
                            // Clear selection and exit selection mode
                            selectedMessages = emptySet()
                            isSelectionMode = false
                            showDeleteDialog = false
                        }
                    ) {
                        Text("Delete", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteDialog = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
        }
    }
}
//
//@Preview
//@Composable
//fun AppAndroidPreview() {
//    // Note: This preview won't work with BluetoothManager as it requires Android context
//    // This is just for UI preview purposes
//    Text("Preview not available for Bluetooth functionality")
//}
//                label = { Text("Type message...") },
//                modifier = Modifier.weight(1f)
//            )
//            Spacer(modifier = Modifier.width(8.dp))
//            Button(
//                onClick = {
//                    if (inputText.isNotBlank()) {
//                        val newMessage = "${if (isServer) "Server" else "Client"}: $inputText"
//                        globalMessages.add(newMessage)
//                        messages = globalMessages.toList()
//                        onSendMessage(inputText) // Send the message to other device
//                        inputText = ""
//                }
//            ) {
//                Text("Send")
//            }
//        }
//    }
//}
