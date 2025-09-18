package chat.transport

import chat.model.Envelope
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
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
        val s = client.webSocketSession(host = host, port = port, path = "/ws")
        session = s
        scope.launch {
            try {
                for (frame in s.incoming) {
                    if (frame is Frame.Text) {
                        val txt = frame.readText()
                        println("KtorTransport received: $txt")
                        val env = json.decodeFromString(Envelope.serializer(), txt)
                        println("KtorTransport decoded envelope: $env")
                        _incoming.emit(env)
                    }
                }
            } catch (t: Throwable) {
                // ignore for now
            }
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
