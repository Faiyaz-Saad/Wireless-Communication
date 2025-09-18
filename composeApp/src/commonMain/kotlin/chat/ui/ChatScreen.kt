package chat.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import chat.model.ChatMessage
import chat.model.Envelope
import chat.transport.ChatTransport
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import productpurchasing.composeapp.generated.resources.Res
import productpurchasing.composeapp.generated.resources.chat_bg
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalResourceApi::class, ExperimentalEncodingApi::class)
@Composable
fun ChatScreen(transport: ChatTransport, me: String, onPickImage: suspend () -> ByteArray?) {
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }

    LaunchedEffect(transport) {
        transport.incoming.collect { env ->
            println("ChatScreen received envelope: $env")
            when (env) {
                is Envelope.Msg -> {
                    println("Adding message to UI: ${env.message}")
                    messages.add(env.message)
                }
                else -> {
                    println("Received other envelope type: ${env::class.simpleName}")
                }
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(Res.drawable.chat_bg),
            contentDescription = "Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Column(Modifier.fillMaxSize().padding(29.dp)) { // 8
            LazyColumn(Modifier.weight(1f)) {
                items(messages) { m ->
                    Column(Modifier.fillMaxWidth().padding(4.dp)) {
                        Text(
                            "${m.from} • ${
                                java.text.SimpleDateFormat("HH:mm:ss")
                                    .format(java.util.Date(m.timestamp))
                            }"
                        )
                        m.text?.let { Text(it) }
                        m.imageBase64?.let { b64 ->
                            // For now, just show a placeholder for images
                            // Platform-specific image handling will be implemented separately
                            Text(
                                text = "📷 Image received",
                                modifier = Modifier
                                    .height(160.dp)
                                    .fillMaxWidth()
                                    .clickable { /* full view */ }
                            )
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val text = input; input = ""
                        scope.launch {
                            val msg = ChatMessage(
                                id = java.util.UUID.randomUUID().toString(),
                                from = me,
                                text = text
                            )
                            transport.send(Envelope.Msg(msg))
                            // Don't add to messages here - let the incoming flow handle it
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF000300)
                    )

                ) {
                    Text("Send")
                }

                Button(onClick = {
                    scope.launch {
                        try {
                            val bytes = onPickImage()
                            if (bytes != null) {
                                val b64 = Base64.encode(bytes)
                                val msg = ChatMessage(
                                    id = java.util.UUID.randomUUID().toString(),
                                    from = me,
                                    imageBase64 = b64
                                )
                                transport.send(Envelope.Msg(msg))
                                // Don't add to messages here - let the incoming flow handle it
                            }
                        } catch (e: Exception) {
                            // Handle any errors gracefully
                            println("Error picking image: ${e.message}")
                        }
                    }
                }) { Text("Image") }
            }
        }
    }
}
