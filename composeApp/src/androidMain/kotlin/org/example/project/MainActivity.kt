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
import chat.transport.ChatTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.Flow
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

            // Show UI based on connection state
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

                    // Try to connect in background
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