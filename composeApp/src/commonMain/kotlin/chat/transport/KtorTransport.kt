package chat.transport

import chat.model.Envelope
import chat.model.ChatMessage
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class KtorTransport(
    private val client: HttpClient
) : ChatTransport {
    private val json = Json { ignoreUnknownKeys = true }
    private val _incoming = MutableSharedFlow<Envelope>(extraBufferCapacity = 64)
    override val incoming = _incoming.asSharedFlow()
    private var session: DefaultClientWebSocketSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override suspend fun startClient(host: String, port: Int) {
        try {
            println("KtorTransport: Connecting to $host:$port/ws")
            val s = client.webSocketSession(host = host, port = port, path = "/ws")
            session = s
            println("KtorTransport: WebSocket session established")
            
            scope.launch {
                try {
                    for (frame in s.incoming) {
                        if (frame is Frame.Text) {
                            val txt = frame.readText()
                            println("KtorTransport received: $txt")
                            
                            try {
                                val env = json.decodeFromString(Envelope.serializer(), txt)
                                println("KtorTransport decoded envelope: $env")
                                _incoming.emit(env)
                            } catch (e: Exception) {
                                println("KtorTransport: Failed to decode message as Envelope: ${e.message}")
                                // Create a simple text message envelope
                                val chatMessage = ChatMessage(
                                    id = "msg_${System.currentTimeMillis()}",
                                    from = "unknown",
                                    text = txt,
                                    timestamp = System.currentTimeMillis()
                                )
                                val textEnvelope = Envelope.Msg(chatMessage)
                                _incoming.emit(textEnvelope)
                            }
                        }
                    }
                } catch (t: Throwable) {
                    println("KtorTransport: Error in message loop: ${t.message}")
                    println("KtorTransport: Stack trace: ${t.stackTrace.joinToString("\n")}")
                }
            }
            println("KtorTransport: Client started successfully")
        } catch (e: Exception) {
            println("KtorTransport: Failed to start client: ${e.message}")
            println("KtorTransport: Stack trace: ${e.stackTrace.joinToString("\n")}")
            throw e
        }
    }

    override suspend fun send(envelope: Envelope) {
        val txt = json.encodeToString(Envelope.serializer(), envelope)
        session?.send(Frame.Text(txt))
    }

    override suspend fun close() {
        try { session?.close() } catch (_: Throwable) {}
        scope.cancel()
    }
}
