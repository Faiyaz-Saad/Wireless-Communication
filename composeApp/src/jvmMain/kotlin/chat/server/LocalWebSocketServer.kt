package chat.server

import java.net.*
import java.io.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.security.MessageDigest
import java.util.Base64

class LocalWebSocketServer(private val port: Int = 8765) {
    private val clients = CopyOnWriteArraySet<WebSocketClient>()
    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private var serverThread: Thread? = null

    fun start() {
        if (running.get()) return
        
        running.set(true)
        serverThread = Thread {
            try {
                serverSocket = ServerSocket(port)
                println("WebSocket server started on port $port")
                
                while (running.get()) {
                    try {
                        val clientSocket = serverSocket?.accept()
                        clientSocket?.let { socket ->
                            val client = WebSocketClient(socket, this)
                            clients.add(client)
                            client.start()
                        }
                    } catch (e: IOException) {
                        if (running.get()) {
                            println("Error accepting connection: ${e.message}")
                        }
                    }
                }
            } catch (e: IOException) {
                println("Server error: ${e.message}")
            }
        }
        serverThread?.start()
    }

    fun stop() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            println("Error closing server: ${e.message}")
        }
        clients.forEach { it.close() }
        clients.clear()
    }

    fun getPort(): Int = port

    fun broadcast(message: String, sender: WebSocketClient? = null) {
        clients.forEach { client ->
            if (client != sender && client.isConnected()) {
                client.send(message)
            }
        }
    }

    fun removeClient(client: WebSocketClient) {
        clients.remove(client)
    }
}

class WebSocketClient(
    private val socket: Socket,
    private val server: LocalWebSocketServer
) {
    private val running = AtomicBoolean(true)
    private var clientThread: Thread? = null
    private var output: OutputStream? = null

    fun start() {
        clientThread = Thread {
            try {
                val input = BufferedReader(InputStreamReader(socket.getInputStream()))
                output = socket.getOutputStream()
                
                // Handle WebSocket handshake
                if (handleWebSocketHandshake(input)) {
                    // Start reading WebSocket frames
                    while (running.get()) {
                        val frame = readWebSocketFrame(input)
                        if (frame == null) break
                        
                        // Broadcast the message to other clients
                        server.broadcast(frame, this)
                    }
                }
            } catch (e: IOException) {
                if (running.get()) {
                    println("Client error: ${e.message}")
                }
            } finally {
                close()
            }
        }
        clientThread?.start()
    }

    private fun handleWebSocketHandshake(input: BufferedReader): Boolean {
        val headers = mutableMapOf<String, String>()
        var line: String?
        
        // Read headers
        while (input.readLine().also { line = it } != null && line!!.isNotEmpty()) {
            if (line!!.contains(":")) {
                val parts = line!!.split(":", limit = 2)
                headers[parts[0].trim().lowercase()] = parts[1].trim()
            }
        }
        
        val webSocketKey = headers["sec-websocket-key"]
        if (webSocketKey != null) {
            val acceptKey = generateAcceptKey(webSocketKey)
            
            val response = """
                HTTP/1.1 101 Switching Protocols
                Upgrade: websocket
                Connection: Upgrade
                Sec-WebSocket-Accept: $acceptKey
                
            """.trimIndent()
            
            output?.write(response.toByteArray())
            output?.flush()
            return true
        }
        return false
    }

    private fun generateAcceptKey(key: String): String {
        val concatenated = key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        val digest = MessageDigest.getInstance("SHA-1").digest(concatenated.toByteArray())
        return Base64.getEncoder().encodeToString(digest)
    }

    private fun readWebSocketFrame(input: BufferedReader): String? {
        try {
            // Simple implementation - read as text for now
            val line = input.readLine()
            return line
        } catch (e: IOException) {
            return null
        }
    }

    fun send(message: String) {
        try {
            // Simple WebSocket frame sending
            val frame = createTextFrame(message)
            output?.write(frame)
            output?.flush()
        } catch (e: IOException) {
            println("Error sending message: ${e.message}")
        }
    }

    private fun createTextFrame(message: String): ByteArray {
        val messageBytes = message.toByteArray()
        val frame = ByteArray(messageBytes.size + 2)
        frame[0] = 0x81.toByte() // FIN + text frame
        frame[1] = messageBytes.size.toByte() // payload length
        System.arraycopy(messageBytes, 0, frame, 2, messageBytes.size)
        return frame
    }

    fun close() {
        running.set(false)
        try {
            socket.close()
        } catch (e: IOException) {
            println("Error closing client socket: ${e.message}")
        }
        server.removeClient(this)
    }

    fun isConnected(): Boolean = running.get() && !socket.isClosed
}