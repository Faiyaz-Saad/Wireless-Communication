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
import android.net.wifi.WifiManager
import android.net.DhcpInfo
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
import kotlinx.coroutines.delay
import java.net.DatagramPacket as JavaDatagramPacket
import java.net.DatagramSocket as JavaDatagramSocket
import java.net.InetAddress

class HostService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: ApplicationEngine? = null
    private var broadcasterScope: CoroutineScope? = null

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
                    server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
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

                    // Acquire multicast lock and start UDP broadcaster for discovery
                    val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val lock = wifi.createMulticastLock("wcwi-lock-service").apply {
                        setReferenceCounted(true)
                        acquire()
                    }
                    broadcasterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                    broadcasterScope?.launch {
                        val socket = JavaDatagramSocket()
                        try {
                            socket.broadcast = true
                            val bytes = "SERVER:$port".toByteArray()
                            val addr = getSubnetBroadcastAddress(applicationContext) ?: InetAddress.getByName("255.255.255.255")
                            while (true) {
                                try {
                                    val p = JavaDatagramPacket(bytes, bytes.size, addr, 8888)
                                    socket.send(p)
                                } catch (_: Throwable) { /* ignore and continue */ }
                                delay(2000)
                            }
                        } finally {
                            try { socket.close() } catch (_: Throwable) {}
                            if (lock.isHeld) try { lock.release() } catch (_: Throwable) {}
                        }
                    }

                    // Update notification to show LAN IP
                    updateNotificationWithIp(port)
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
        try { broadcasterScope?.cancel() } catch (_: Throwable) {}
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
            .setContentText("Starting…")
            .build()
    }

    private fun updateNotificationWithIp(port: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ip = getLocalIpAddress(this) ?: "127.0.0.1"
        val notif = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setOngoing(true)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Hosting chat server")
            .setContentText("Running on $ip:$port")
            .build()
        nm.notify(NOTIF_ID, notif)
    }

    private fun getLocalIpAddress(context: Context): String? {
        return try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcp: DhcpInfo = wifi.dhcpInfo ?: return null
            val ip = dhcp.ipAddress
            if (ip == 0) return null
            val quads = ByteArray(4)
            for (k in 0..3) {
                quads[k] = (ip shr (k * 8) and 0xFF).toByte()
            }
            InetAddress.getByAddress(quads).hostAddress
        } catch (_: Throwable) { null }
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

    companion object {
        const val KEY_PORT = "key_port"
        const val DEFAULT_PORT = 9876
        private const val NOTIF_CHANNEL_ID = "host_service_channel"
        private const val NOTIF_ID = 1001
    }
}


