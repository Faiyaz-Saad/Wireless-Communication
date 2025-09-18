package org.example.project

import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.plugins.websocket.*

actual fun platformHttpClient(): HttpClient = HttpClient(Darwin) {
    install(WebSockets)
}
