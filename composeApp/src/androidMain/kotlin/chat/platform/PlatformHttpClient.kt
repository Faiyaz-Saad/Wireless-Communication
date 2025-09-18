package chat.platform

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*

actual fun platformHttpClient(): HttpClient = HttpClient(CIO) {
    install(WebSockets) {
        pingInterval = 20_000
    }
}
