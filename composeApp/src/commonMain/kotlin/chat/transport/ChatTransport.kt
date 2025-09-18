package chat.transport

import chat.model.Envelope
import kotlinx.coroutines.flow.Flow

interface ChatTransport {
    val incoming: Flow<Envelope>
    suspend fun startClient(host: String, port: Int)
    suspend fun send(envelope: Envelope)
    suspend fun close()
}
