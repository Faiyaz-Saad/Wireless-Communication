package org.example.project

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import chat.platform.platformHttpClient
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.ktor.server.response.*
import io.ktor.client.plugins.websocket.*
import android.content.Context
import android.net.wifi.WifiManager
import android.net.DhcpInfo
import java.net.InetAddress

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            // State management for connection
            var connected by remember { mutableStateOf(false) }
            var isServer by remember { mutableStateOf(false) }
            var statusMessage by remember { mutableStateOf("Choose connection mode") }
            var serverIp by remember { mutableStateOf("") }
            var showIpInput by remember { mutableStateOf(false) }
            var clientConnected by remember { mutableStateOf(false) }
            val uiScope = rememberCoroutineScope()
            val serverPort = 9876

                    if (connected) {
                        // Simple chat interface instead of complex ChatScreen
                        SimpleChatScreen(
                            isServer = isServer,
                            serverIp = if (isServer) getLocalIpAddress(applicationContext) ?: "Unknown" else serverIp,
                            serverPort = serverPort,
                            onSendMessage = { message ->
                                // Handle message sending
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
                        text = "Wireless Communication",
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (showIpInput) {
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
                                                                // Add received message to global list
                                                                onMessageReceived?.invoke(msg)
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        println("Error receiving messages: ${e.message}")
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
                    } else {
                        // Server button
                        Button(
                            onClick = {
                                uiScope.launch(Dispatchers.IO) {
                                    try {
                                        withContext(Dispatchers.Main) { 
                                            statusMessage = "Starting server..." 
                                        }
                                        println("=== SERVER STARTUP BEGIN ===")
                                        println("Starting WebSocket server on port $serverPort")
                                        
                                        // Start the server
                                        startWebSocketServer(serverPort) { 
                                            // Callback when client connects
                                            clientConnected = true
                                            connected = true
                                            statusMessage = "Client connected - Chat ready"
                                        }
                                        
                                        // Get IP address
                                        val myIp = getLocalIpAddress(applicationContext) ?: "Unknown"
                                        println("Server started successfully on $myIp:$serverPort")
                                        
                                        // Update UI
                                        withContext(Dispatchers.Main) {
                                            isServer = true
                                            statusMessage = "Server running on $myIp:$serverPort - Waiting for client..."
                                        }
                                        println("=== SERVER STARTUP COMPLETE ===")
                                        
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
                        
                        // Test server button
                        Button(
                            onClick = {
                                uiScope.launch(Dispatchers.IO) {
                                    try {
                                        withContext(Dispatchers.Main) { 
                                            statusMessage = "Testing server..." 
                                        }
                                        val myIp = getLocalIpAddress(applicationContext) ?: "Unknown"
                                        val testUrl = "http://$myIp:$serverPort/test"
                                        println("Testing server at: $testUrl")
                                        
                                        // Simple HTTP test
                                        val response = java.net.URL(testUrl).openConnection()
                                        response.connectTimeout = 5000
                                        response.readTimeout = 5000
                                        val inputStream = response.getInputStream()
                                        val responseText = inputStream.bufferedReader().use { it.readText() }
                                        
                                        withContext(Dispatchers.Main) { 
                                            statusMessage = "Server test: $responseText"
                                        }
                                        println("Server test response: $responseText")
                                        
                                    } catch (e: Exception) {
                                        println("Server test failed: ${e.message}")
                                        withContext(Dispatchers.Main) { 
                                            statusMessage = "Server test failed: ${e.message}"
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Test Server")
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        
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

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = "Instructions:\n1. One device: Start Server\n2. Other device: Enter server IP and connect",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

// Simple chat screen that doesn't use the complex transport system
@Composable
fun SimpleChatScreen(
    isServer: Boolean,
    serverIp: String,
    serverPort: Int,
    onSendMessage: (String) -> Unit
) {
    var messages by remember { mutableStateOf(globalMessages.toList()) }
    var inputText by remember { mutableStateOf("") }
    val uiScope = rememberCoroutineScope()
    
    // Set up message reception callback
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
        Text(
            text = if (isServer) "Server Mode - IP: $serverIp:$serverPort" else "Client Mode - Connected to $serverIp:$serverPort",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Messages display
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
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Type message...") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (inputText.isNotBlank()) {
                        val newMessage = "${if (isServer) "Server" else "Client"}: $inputText"
                        globalMessages.add(newMessage)
                        messages = globalMessages.toList()
                        onSendMessage(inputText) // Send the message to other device
                        inputText = ""
                        println("Sending message: $newMessage")
                    }
                }
            ) {
                Text("Send")
            }
        }
    }
}

// Global variables to store WebSocket sessions
private var serverClients = mutableSetOf<DefaultWebSocketServerSession>()
private var clientSession: DefaultClientWebSocketSession? = null

// Global message state for sharing between WebSocket handlers and UI
private var globalMessages = mutableListOf<String>()
private var onMessageReceived: ((String) -> Unit)? = null

// Function to send message from server to all clients
private suspend fun sendMessageToClients(message: String) {
    println("Server sending message to clients: $message")
    val deadClients = mutableSetOf<DefaultWebSocketServerSession>()
    serverClients.forEach { client ->
        try {
            client.send(Frame.Text("Server: $message"))
        } catch (e: Exception) {
            println("Error sending to client: ${e.message}")
            deadClients.add(client)
        }
    }
    serverClients.removeAll(deadClients)
}

// Function to send message from client to server
private suspend fun sendMessageToServer(message: String) {
    println("Client sending message to server: $message")
    try {
        clientSession?.send(Frame.Text("Client: $message"))
    } catch (e: Exception) {
        println("Error sending to server: ${e.message}")
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    SimpleChatScreen(
        isServer = true,
        serverIp = "192.168.1.100",
        serverPort = 9876,
        onSendMessage = { }
    )
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
                                        
                                        // Add received message to global list
                                        onMessageReceived?.invoke(msg)
                                        
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

// Helper function to get local IP address
private fun getLocalIpAddress(context: Context): String? {
    return try {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
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