package org.example.project

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import chat.platform.platformHttpClient
import chat.transport.KtorTransport
import chat.ui.ChatScreen
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.isActive
import chat.transport.ChatTransport
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.Flow
import android.net.wifi.WifiManager
import android.net.DhcpInfo
import android.content.Context
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.DatagramSocket as JavaDatagramSocket
import java.net.DatagramPacket as JavaDatagramPacket
import java.net.InetAddress
import chat.model.Envelope

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val client = platformHttpClient()
        val transport = KtorTransport(client)

        setContent {
            // State to track if connected and server info
            var connected by remember { mutableStateOf(false) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            val uiScope = rememberCoroutineScope()
            var serverStarted by remember { mutableStateOf(false) }
            val serverPort = 9876

            // Host or Join controls
            if (connected) {
                ChatScreen(
                    transport = transport,
                    me = "AndroidUser",
                    onPickImage = { null }
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

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            text = "Connecting to server...",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    // Try to connect in background (Join flow)
                    LaunchedEffect(Unit) {
                        val serverInfo = listenForServerBroadcast()
                        if (serverInfo != null) {
                            val (host, port) = serverInfo
                            if (host != null) {
                                try {
                                    transport.startClient(host, port)
                                    connected = true // Switch to ChatScreen
                                } catch (e: Exception) {
                                    errorMessage = "Connection error:  ${e.message}"
                                }
                            } else {
                                errorMessage = "Server host is null."
                            }
                        } else {
                            errorMessage = "No server found on the network."
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    // Host button to start server on Android (Foreground Service)
                    androidx.compose.material3.Button(onClick = {
                        // Acquire multicast lock for discovery (main thread OK)
                        try {
                            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                            val lock = wifi.createMulticastLock("wcwi-lock")
                            lock.setReferenceCounted(true)
                            lock.acquire()
                        } catch (_: Throwable) {}

                        if (serverStarted) return@Button
                        serverStarted = true

                        // Start foreground service to host Ktor server
                        val intent = android.content.Intent(applicationContext, HostService::class.java)
                        intent.putExtra(HostService.KEY_PORT, serverPort)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            applicationContext.startForegroundService(intent)
                        } else {
                            applicationContext.startService(intent)
                        }

                        // Connect self as client with simple retry to avoid race with service start
                        uiScope.launch(Dispatchers.IO) {
                            var lastError: String? = null
                            repeat(5) { attempt ->
                                try {
                                    transport.startClient("127.0.0.1", serverPort)
                                    withContext(Dispatchers.Main) { connected = true; errorMessage = null }
                                    return@launch
                                } catch (e: Exception) {
                                    lastError = e.message
                                    kotlinx.coroutines.delay(400L * (attempt + 1))
                                }
                            }
                            withContext(Dispatchers.Main) { errorMessage = "Failed to connect to 127.0.0.1:$serverPort: ${'$'}lastError" }
                        }
                    }) { Text("Host on this device") }
                }
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    // Preview with mock transport
    ChatScreen(
        transport = object : ChatTransport {
            override val incoming: Flow<Envelope> = emptyFlow()
            override suspend fun startClient(host: String, port: Int) {}
            override suspend fun send(envelope: Envelope) {}
            override suspend fun close() {}
        },
        me = "Preview",
        onPickImage = { null }
    )
}

suspend fun listenForServerBroadcast(port: Int = 8888): Pair<String?, Int>? {
    return withContext(Dispatchers.IO) {
        val socket = JavaDatagramSocket(port)
        socket.broadcast = true
        val buffer = ByteArray(1024)
        val packet = JavaDatagramPacket(buffer, buffer.size)
        socket.soTimeout = 10000 // 10 seconds timeout
        try {
            socket.receive(packet) // This will block until a packet is received or timeout occurs
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

private fun getSubnetBroadcastAddress(context: Context): InetAddress? {
    return try {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcp: DhcpInfo = wifi.dhcpInfo ?: return null
        val ip = dhcp.ipAddress
        val mask = dhcp.netmask
        if (ip == 0 || mask == 0) return null
        val broadcastInt = (ip and mask) or mask.inv()
        val quads = ByteArray(4)
        for (k in 0..3) {
            quads[k] = (broadcastInt shr (k * 8) and 0xFF).toByte()
        }
        InetAddress.getByAddress(quads)
    } catch (_: Throwable) {
        null
    }
}