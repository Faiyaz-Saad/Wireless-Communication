package org.example.project

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class HostService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: ApplicationEngine? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra(KEY_PORT, DEFAULT_PORT) ?: DEFAULT_PORT
        if (server == null) {
            serviceScope.launch {
                try {
                    server = embeddedServer(CIO, port = port, host = "127.0.0.1") {
                        install(WebSockets)
                        routing {
                            webSocket("/ws") {
                                try {
                                    for (frame in incoming) {
                                        if (frame is Frame.Text) {
                                            val msg = frame.readText()
                                            send(Frame.Text(msg))
                                        }
                                    }
                                } catch (_: Throwable) {}
                            }
                        }
                    }.start(false)
                } catch (_: Throwable) {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try { server?.stop(1000, 1000) } catch (_: Throwable) {}
        server = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val channelId = NOTIF_CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(channelId, "Hosting", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, channelId)
            .setOngoing(true)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Hosting chat server")
            .setContentText("Running on 127.0.0.1")
            .build()
    }

    companion object {
        const val KEY_PORT = "key_port"
        const val DEFAULT_PORT = 9876
        private const val NOTIF_CHANNEL_ID = "host_service_channel"
        private const val NOTIF_ID = 1001
    }
}


