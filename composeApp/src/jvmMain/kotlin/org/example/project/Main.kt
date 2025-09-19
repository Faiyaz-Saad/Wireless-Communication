package org.example.project

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.exitApplication
import androidx.compose.runtime.rememberCoroutineScope
import chat.platform.platformHttpClient
import chat.transport.KtorTransport
import chat.ui.ChatScreen
import kotlinx.coroutines.launch
import javax.swing.JFileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import chat.model.Envelope
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

fun startUdpBroadcast(port: Int = 8888, message: String = "SERVER:8765") {
    Thread {
        val socket = DatagramSocket()
        socket.broadcast = true
        val buffer = message.toByteArray()
        val address = InetAddress.getByName("255.255.255.255")
        while (true) {
            try {
                val packet = DatagramPacket(buffer, buffer.size, address, port)
                socket.send(packet)
                Thread.sleep(2000) // broadcast every 2 seconds
            } catch (e: Exception) {
                println("UDP broadcast error: ${e.message}")
                break
            }
        }
        socket.close()
    }.start()
}

suspend fun listenForServerBroadcast(port: Int = 8888): Pair<String?, Int>? {
    return withContext(Dispatchers.IO) {
        val socket = DatagramSocket(port)
        socket.broadcast = true
        val buffer = ByteArray(1024)
        val packet = DatagramPacket(buffer, buffer.size)
        socket.soTimeout = 10000 // 10 seconds timeout
        try {
            socket.receive(packet)
            val msg = String(packet.data, 0, packet.length)
            socket.close()
            if (msg.startsWith("SERVER:")) {
                val serverPort = msg.substringAfter(":").toInt()
                val host = packet.address.hostAddress
                Pair(host, serverPort)
            } else null
        } catch (e: Exception) {
            socket.close()
            null
        }
    }
}

fun main() = application {
    // Shared flow for broadcasting messages to all clients
    val clients = mutableSetOf<DefaultWebSocketServerSession>()
    val json = Json { ignoreUnknownKeys = true }
    
    // Start Ktor WebSocket server
    val server = embeddedServer(Netty, port = 8765) {
        install(WebSockets) {
            pingPeriod = java.time.Duration.ofSeconds(15)
            timeout = java.time.Duration.ofSeconds(15)
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }
        routing {
            webSocket("/ws") {
                println("Client connected")
                clients.add(this)
                try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val message = frame.readText()
                            println("Received: $message")
                            
                            // Broadcast to all connected clients except sender
                            val deadClients = mutableSetOf<DefaultWebSocketServerSession>()
                            clients.forEach { client ->
                                try {
                                    if (client != this) {
                                        client.send(Frame.Text(message))
                                    }
                                } catch (e: Exception) {
                                    println("Error sending to client: ${e.message}")
                                    deadClients.add(client)
                                }
                            }
                            // Remove dead clients
                            clients.removeAll(deadClients)
                        }
                    }
                } catch (e: Exception) {
                    println("WebSocket error: ${e.message}")
                } finally {
                    clients.remove(this)
                    println("Client disconnected. Remaining clients: ${clients.size}")
                }
            }
        }
    }
    server.start(wait = false)
    
    startUdpBroadcast() // Broadcast server presence

    val client = platformHttpClient()
    val transport = KtorTransport(client)

    Window(onCloseRequest = { 
        try {
            transport.close()
            server.stop(1000, 2000)
        } catch (e: Exception) {
            println("Error during cleanup: ${e.message}")
        }
        exitApplication() 
    }, title = "Wireless Communication Without Internet (WCWI)") {
        androidx.compose.runtime.rememberCoroutineScope().launch {
            // Connect to self for demo; Android will auto-discover via UDP
            transport.startClient("127.0.0.1", 8765)
        }

        ChatScreen(
            transport = transport,
            me = "Desktop",
            onPickImage = {
                try {
                    val chooser = JFileChooser()
                    chooser.dialogTitle = "Select image"
                    val res = chooser.showOpenDialog(null)
                    if (res == JFileChooser.APPROVE_OPTION) {
                        val f = chooser.selectedFile
                        f.readBytes()
                    } else null
                } catch (e: Exception) {
                    println("Error selecting image: ${e.message}")
                    null
                }
            }
        )
    }
}
