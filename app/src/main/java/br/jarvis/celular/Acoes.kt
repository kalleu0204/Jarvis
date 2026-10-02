package br.jarvis.celular

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import android.view.KeyEvent
import org.json.JSONObject
import kotlin.math.roundToInt

object Acoes {
    private var toque: Ringtone? = null
    private const val SEM_ACESSIBILIDADE = "Ative a acessibilidade do Jarvis no app."

    fun executar(ctx: Context, d: JSONObject): JSONObject {
        return when (d.optString("acao")) {
            "bateria" -> bateria(ctx)
            "midia" -> midia(ctx, d)
            "volume" -> volume(ctx, d)
            "achar" -> achar(ctx)
            "parar_toque" -> pararToque()
            "alarme" -> alarme(ctx, d)
            "alarme_desativar" -> alarmeDesativar(ctx, d)
            "timer" -> timer(ctx, d)
            "notificacoes" -> notificacoes()
            "lanterna" -> lanterna(ctx, d)
            "tecla" -> tecla(d)
            "app" -> app(ctx, d)
            "buscar_musica" -> buscarMusica(ctx, d)
            "playlist" -> playlist(ctx, d)
            "contato" -> contato(ctx, d)
            "sms" -> sms(ctx, d)
            "ligar" -> ligar(ctx, d)
            "whatsapp" -> whatsapp(ctx, d)
            "brilho" -> brilho(ctx, d)
            "pesquisar" -> pesquisar(ctx, d)
            "ler_tela" -> lerTela()
            "clicar" -> clicar(d)
            "tocar" -> tocar(d)
            "digitar" -> digitar(d)
            else -> falha("Não conheço essa ação.")
        }
    }

    private fun ok(msg: String): JSONObject = JSONObject().put("ok", true).put("msg", msg)

    private fun falha(msg: String): JSONObject = JSONObject().put("ok", false).put("msg", msg)

    private fun abrir(ctx: Context, i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    @Suppress("DEPRECATION")
    private fun acordar(ctx: Context) {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "jarvis:acordar"
        )
        wl.acquire(5000)
    }

    private fun soDigitos(s: String): String {
        val n = s.filter { it.isDigit() }
        return if (n.length > 11) n else "55$n"
    }

    // ------------------------------------------------------------------ simples
    private fun bateria(ctx: Context): JSONObject {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val p = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val carregando = bm.isCharging
        val msg = "A bateria do celular está em $p por cento" + (if (carregando) ", carregando" else "") + "."
        return ok(msg).put("pct", p)
    }

    private fun midia(ctx: Context, d: JSONObject): JSONObject {
        val tecla = d.optString("tecla", "toggle")
        val codigo = when (tecla) {
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, codigo))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, codigo))
        val msg = when (tecla) {
            "next" -> "Passei."
            "prev" -> "Voltei."
            "pause" -> "Pausei."
            "play" -> "Tocando."
            else -> "Pronto."
        }
        return ok(msg)
    }

    private fun volume(ctx: Context, d: JSONObject): JSONObject {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (d.has("pct")) {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val pct = d.getInt("pct")
            val v = (max * pct / 100.0).roundToInt()
            am.setStreamVolume(AudioManager.STREAM_MUSIC, v, AudioManager.FLAG_SHOW_UI)
            return ok("Volume em $pct por cento.")
        }
        val dir = if (d.optString("dir") == "up") AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        repeat(2) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI) }
        return ok("Pronto.")
    }

    private fun achar(ctx: Context): JSONObject {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val t = RingtoneManager.getRingtone(ctx, uri)
        t.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        t.play()
        toque = t
        Handler(Looper.getMainLooper()).postDelayed({ t.stop() }, 20000)
        return ok("Tocando o celular.")
    }

    private fun pararToque(): JSONObject {
        toque?.stop()
        return ok("Parei.")
    }

    private fun alarme(ctx: Context, d: JSONObject): JSONObject {
        val h = d.getInt("h")
        val m = d.getInt("m")
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, h)
            .putExtra(AlarmClock.EXTRA_MINUTES, m)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Jarvis")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        abrir(ctx, i)
        return ok("Alarme marcado para %02d:%02d.".format(h, m))
    }

    private fun alarmeDesativar(ctx: Context, d: JSONObject): JSONObject {
        val i = Intent(AlarmClock.ACTION_DISMISS_ALARM)
        if (d.has("h")) {
            i.putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_TIME)
            i.putExtra(AlarmClock.EXTRA_HOUR, d.getInt("h"))
            i.putExtra(AlarmClock.EXTRA_MINUTES, d.getInt("m"))
        } else {
            i.putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_NEXT)
        }
        if (i.resolveActivity(ctx.packageManager) != null) {
            abrir(ctx, i)
            return ok("Pedi ao relógio para desativar o alarme.")
        }
        abrir(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS))
        return ok("Seu relógio não aceita desativar por comando. Abri a lista de alarmes.")
    }

    private fun timer(ctx: Context, d: JSONObject): JSONObject {
        val s = d.getInt("s")
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, s)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        abrir(ctx, i)
        val mins = s / 60
        return ok("Timer de " + (if (mins > 0) "$mins minutos" else "$s segundos") + " iniciado.")
    }

    private fun notificacoes(): JSONObject {
        val n = JarvisNotifs.instance ?: return falha("Ative o acesso às notificações no app Jarvis.")
        val itens = mutableListOf<String>()
        for (sbn in n.activeNotifications) {
            if (sbn.packageName == "br.jarvis.celular" || sbn.isOngoing) continue
            val e = sbn.notification.extras
            val t = e.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
            val x = e.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
            if (t.isNotBlank() || x.isNotBlank()) itens.add("$t: $x")
        }
        if (itens.isEmpty()) return ok("Você não tem notificações.")
        return ok("Você tem ${itens.size} notificações. " + itens.take(4).joinToString(". "))
    }

    private fun lanterna(ctx: Context, d: JSONObject): JSONObject {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return falha("Não achei a lanterna.")
        cm.setTorchMode(id, d.optBoolean("on", true))
        return ok("Pronto.")
    }

    private fun tecla(d: JSONObject): JSONObject {
        val a = JarvisAccess.instance ?: return falha(SEM_ACESSIBILIDADE)
        val acao = when (d.optString("nome")) {
            "home" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
            "back" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
            "recents" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
            "lock" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
            else -> return falha("Não conheço essa tecla.")
        }
        a.performGlobalAction(acao)
        return ok("Pronto.")
    }

    private fun app(ctx: Context, d: JSONObject): JSONObject {
        val pm = ctx.packageManager
        val nome = d.optString("nome")
        val pac = d.optString("pacote")
        var intent: Intent? = if (pac.isNotEmpty()) pm.getLaunchIntentForPackage(pac) else null
        if (intent == null && nome.isNotEmpty()) {
            val alvo = norm(nome)
            @Suppress("DEPRECATION")
            val achado = pm.getInstalledApplications(0).firstOrNull {
                norm(it.loadLabel(pm).toString()).contains(alvo) && pm.getLaunchIntentForPackage(it.packageName) != null
            }
            if (achado != null) intent = pm.getLaunchIntentForPackage(achado.packageName)
        }
        if (intent == null) return falha("Não achei o app $nome no celular.")
        acordar(ctx)
        abrir(ctx, intent)
        return ok("Abrindo $nome.")
    }

    private fun pesquisar(ctx: Context, d: JSONObject): JSONObject {
        val q = d.getString("q")
        abrir(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))
        return ok("Pesquisando $q no celular.")
    }

    private fun brilho(ctx: Context, d: JSONObject): JSONObject {
        if (!Settings.System.canWrite(ctx)) return falha("Libere alterar configurações do sistema no app Jarvis.")
        val pct = d.getInt("pct")
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (255 * pct / 100.0).roundToInt())
        return ok("Brilho em $pct por cento.")
    }

    // ------------------------------------------------------------------- musica
    private fun buscarMusica(ctx: Context, d: JSONObject): JSONObject {
        val pac = d.optString("pacote").ifEmpty { "com.spotify.music" }
        val i = Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH")
            .setPackage(pac)
            .putExtra("query", d.getString("q"))
            .putExtra("android.intent.extra.focus", "vnd.android.cursor.item/*")
        return try {
            acordar(ctx)
            abrir(ctx, i)
            ok("Procurando " + d.getString("q") + ".")
        } catch (e: ActivityNotFoundException) {
            falha("O app de música não está instalado.")
        }
    }

    private fun playlist(ctx: Context, d: JSONObject): JSONObject {
        val nome = d.getString("nome")
        val pac = d.optString("pacote").ifEmpty { "com.spotify.music" }
        val pm = ctx.packageManager
        val lancar = pm.getLaunchIntentForPackage(pac) ?: return falha("O app de música não está instalado.")
        acordar(ctx)
        val acc = JarvisAccess.instance
        if (acc == null) {
            val i = Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH")
                .setPackage(pac)
                .putExtra("query", nome)
                .putExtra("android.intent.extra.focus", "vnd.android.cursor.item/playlist")
                .putExtra("android.intent.extra.playlist", nome)
            abrir(ctx, i)
            return ok("Procurando a playlist $nome.")
        }
        abrir(ctx, lancar)
        Thread.sleep(3000)
        if (!acc.esperarEClicar(emptyList(), listOf("sua biblioteca", "your library"), 6000, false))
            return falha("Não achei a biblioteca do app de música.")
        Thread.sleep(1500)
        if (!acc.esperarEClicar(emptyList(), listOf(nome), 6000, false))
            return falha("Não achei a playlist $nome na sua biblioteca.")
        Thread.sleep(1500)
        val tocou = acc.esperarEClicar(emptyList(), listOf("reproduzir", "play", "tocar"), 5000, true)
        return if (tocou) ok("Tocando a playlist $nome.") else ok("Abri a playlist $nome. Toque em reproduzir.")
    }

    // ----------------------------------------------------- contatos e mensagens
    private fun contato(ctx: Context, d: JSONObject): JSONObject {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            return falha("Libere o acesso aos contatos no app Jarvis.")
        val alvo = norm(d.optString("nome"))
        var melhorNome = ""
        var melhorNumero = ""
        var pontos = 0
        val c = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )
        if (c != null) {
            while (c.moveToNext()) {
                val nome = c.getString(0) ?: continue
                val num = c.getString(1) ?: continue
                val n = norm(nome)
                if (n.isEmpty() || alvo.isEmpty()) continue
                val partesN = n.split(" ")
                val p = when {
                    n == alvo -> 4
                    n.startsWith(alvo) || alvo.startsWith(n) -> 3
                    n.contains(alvo) || alvo.contains(n) -> 2
                    alvo.split(" ").any { t -> t.length > 2 && partesN.contains(t) } -> 1
                    else -> 0
                }
                if (p > pontos) {
                    pontos = p
                    melhorNome = nome
                    melhorNumero = num
                }
            }
            c.close()
        }
        if (pontos == 0) return falha("Não achei " + d.optString("nome") + " nos contatos.")
        return ok("ok").put("nome", melhorNome).put("numero", melhorNumero)
    }

    @Suppress("DEPRECATION")
    private fun sms(ctx: Context, d: JSONObject): JSONObject {
        if (ctx.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
            return falha("Libere o envio de SMS no app Jarvis.")
        val sm = SmsManager.getDefault()
        val partes = sm.divideMessage(d.getString("texto"))
        sm.sendMultipartTextMessage(d.getString("numero"), null, partes, null, null)
        return ok("Mensagem enviada.")
    }

    private fun ligar(ctx: Context, d: JSONObject): JSONObject {
        if (ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED)
            return falha("Libere ligações no app Jarvis.")
        abrir(ctx, Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(d.getString("numero")))))
        return ok("Ligando para " + d.optString("nome", "o contato") + ".")
    }

    private fun whatsapp(ctx: Context, d: JSONObject): JSONObject {
        val num = soDigitos(d.getString("numero"))
        val url = "https://wa.me/$num?text=" + Uri.encode(d.getString("texto"))
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.whatsapp")
        acordar(ctx)
        try {
            abrir(ctx, i)
        } catch (e: ActivityNotFoundException) {
            return falha("O WhatsApp não está instalado.")
        }
        val acc = JarvisAccess.instance
            ?: return ok("Abri a conversa com a mensagem pronta. Toque em enviar.")
        val enviou = acc.esperarEClicar(
            listOf("com.whatsapp:id/send"), listOf("enviar", "send"), 12000, true
        )
        return if (enviou) ok("Mensagem enviada.")
        else ok("Abri a conversa, mas não achei o botão de enviar. Toque nele.")
    }

    // --------------------------------------------------------- controle da tela
    private fun lerTela(): JSONObject {
        val a = JarvisAccess.instance ?: return falha(SEM_ACESSIBILIDADE)
        val t = a.lerTela()
        return if (t.isEmpty()) falha("Não consegui ler a tela.") else ok("Na tela: $t")
    }

    private fun clicar(d: JSONObject): JSONObject {
        val a = JarvisAccess.instance ?: return falha(SEM_ACESSIBILIDADE)
        val clicou = a.esperarEClicar(emptyList(), listOf(d.getString("texto")), 3000, false)
        return if (clicou) ok("Pronto.") else falha("Não achei " + d.getString("texto") + " na tela.")
    }

    private fun tocar(d: JSONObject): JSONObject {
        val a = JarvisAccess.instance ?: return falha(SEM_ACESSIBILIDADE)
        a.tocar(d.getDouble("x").toFloat(), d.getDouble("y").toFloat())
        return ok("Pronto.")
    }

    private fun digitar(d: JSONObject): JSONObject {
        val a = JarvisAccess.instance ?: return falha(SEM_ACESSIBILIDADE)
        return if (a.digitar(d.getString("texto"))) ok("Digitei.") else falha("Não tem campo de texto selecionado.")
    }
}
