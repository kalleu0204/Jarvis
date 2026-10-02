package br.jarvis.celular

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import kotlin.concurrent.thread

class JarvisService : Service() {
    companion object {
        @Volatile
        var rodando = false
        const val PORTA = 8765
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val canal = "jarvis"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(canal, "Jarvis", NotificationManager.IMPORTANCE_LOW))
        val notif = Notification.Builder(this, canal)
            .setContentTitle("Jarvis conectado")
            .setContentText("Aguardando comandos na porta $PORTA")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notif)
        }
        if (!rodando) {
            rodando = true
            thread(isDaemon = true) { servir() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        rodando = false
        super.onDestroy()
    }

    private fun servir() {
        try {
            val ss = ServerSocket()
            ss.reuseAddress = true
            ss.bind(InetSocketAddress(PORTA))
            while (rodando) {
                val s = ss.accept()
                thread(isDaemon = true) { atender(s) }
            }
        } catch (e: Exception) {
            rodando = false
        }
    }

    private fun lerLinha(i: InputStream): String? {
        val sb = ByteArrayOutputStream()
        while (true) {
            val b = i.read()
            if (b < 0) return if (sb.size() == 0) null else sb.toString("UTF-8")
            if (b == 10) break
            if (b != 13) sb.write(b)
        }
        return sb.toString("UTF-8")
    }

    private fun responder(s: Socket, codigo: Int, obj: JSONObject) {
        val dados = obj.toString().toByteArray(Charsets.UTF_8)
        val cab = "HTTP/1.1 $codigo OK\r\nContent-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${dados.size}\r\nConnection: close\r\n\r\n"
        val saida = s.getOutputStream()
        saida.write(cab.toByteArray(Charsets.UTF_8))
        saida.write(dados)
        saida.flush()
    }

    private fun atender(s: Socket) {
        try {
            s.soTimeout = 60000
            val ip = s.inetAddress
            val permitido = ip.isLoopbackAddress || ip.isSiteLocalAddress ||
                (ip.hostAddress ?: "").startsWith("100.")
            val ent = BufferedInputStream(s.getInputStream())
            lerLinha(ent) ?: return
            var tamanho = 0
            var token = ""
            while (true) {
                val h = lerLinha(ent) ?: break
                if (h.isEmpty()) break
                val p = h.indexOf(':')
                if (p > 0) {
                    val k = h.substring(0, p).trim().lowercase()
                    val v = h.substring(p + 1).trim()
                    if (k == "content-length") tamanho = v.toIntOrNull() ?: 0
                    if (k == "x-token") token = v
                }
            }
            val corpo = ByteArray(tamanho)
            var lido = 0
            while (lido < tamanho) {
                val n = ent.read(corpo, lido, tamanho - lido)
                if (n < 0) break
                lido += n
            }
            if (!permitido) {
                responder(s, 403, JSONObject().put("ok", false).put("msg", "Origem não permitida."))
                return
            }
            val salvo = getSharedPreferences("jarvis", MODE_PRIVATE).getString("token", "") ?: ""
            if (salvo.isEmpty() || !MessageDigest.isEqual(token.toByteArray(), salvo.toByteArray())) {
                responder(s, 401, JSONObject().put("ok", false).put("msg", "Senha errada."))
                return
            }
            val resp = try {
                Acoes.executar(applicationContext, JSONObject(String(corpo, Charsets.UTF_8)))
            } catch (e: Exception) {
                JSONObject().put("ok", false).put("msg", "Deu erro no celular: " + (e.message ?: "desconhecido"))
            }
            responder(s, 200, resp)
        } catch (e: Exception) {
        } finally {
            try {
                s.close()
            } catch (e: Exception) {
            }
        }
    }
}
