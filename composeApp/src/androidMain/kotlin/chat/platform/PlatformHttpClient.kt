package chat.platform

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.websocket.*

actual fun platformHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(WebSockets) {
        pingInterval = 20_000
    }
}
