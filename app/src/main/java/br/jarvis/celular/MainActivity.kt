package br.jarvis.celular

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var campoSenha: EditText

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val coluna = LinearLayout(this)
        coluna.orientation = LinearLayout.VERTICAL
        coluna.setPadding(pad, pad, pad, pad)

        val titulo = TextView(this)
        titulo.text = "Jarvis Celular"
        titulo.textSize = 24f
        coluna.addView(titulo)

        status = TextView(this)
        status.textSize = 14f
        status.setPadding(0, pad, 0, pad)
        coluna.addView(status)

        campoSenha = EditText(this)
        campoSenha.hint = "Senha (a mesma do TOKEN no jarvis.py)"
        campoSenha.inputType = InputType.TYPE_CLASS_TEXT
        campoSenha.setText(getSharedPreferences("jarvis", MODE_PRIVATE).getString("token", ""))
        coluna.addView(campoSenha)

        coluna.addView(botao("1. Salvar senha e ligar o Jarvis") {
            getSharedPreferences("jarvis", MODE_PRIVATE).edit()
                .putString("token", campoSenha.text.toString().trim()).apply()
            ligar()
            atualizar()
        })
        coluna.addView(botao("2. Permissões (contatos, SMS, ligação)") {
            val lista = mutableListOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.SEND_SMS
            )
            if (Build.VERSION.SDK_INT >= 33) lista.add(Manifest.permission.POST_NOTIFICATIONS)
            requestPermissions(lista.toTypedArray(), 1)
        })
        coluna.addView(botao("3. Permitir configurações restritas (se a acessibilidade estiver bloqueada)") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        })
        coluna.addView(botao("4. Ligar a acessibilidade do Jarvis") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        coluna.addView(botao("5. Permitir ler notificações") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        coluna.addView(botao("6. Aparecer sobre outros apps") {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        })
        coluna.addView(botao("7. Alterar brilho (configurações do sistema)") {
            startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
            )
        })
        coluna.addView(botao("8. Bateria: sem restrição") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })

        val rolagem = ScrollView(this)
        rolagem.addView(coluna)
        setContentView(rolagem)
    }

    override fun onResume() {
        super.onResume()
        val token = getSharedPreferences("jarvis", MODE_PRIVATE).getString("token", "") ?: ""
        if (token.isNotEmpty() && !JarvisService.rodando) ligar()
        atualizar()
    }

    private fun botao(texto: String, acao: () -> Unit): Button {
        val bt = Button(this)
        bt.text = texto
        bt.isAllCaps = false
        bt.setOnClickListener { acao() }
        return bt
    }

    private fun ligar() {
        try {
            startForegroundService(Intent(this, JarvisService::class.java))
        } catch (e: Exception) {
        }
    }

    private fun ips(): String {
        val lista = mutableListOf<String>()
        try {
            for (ni in NetworkInterface.getNetworkInterfaces().toList()) {
                for (a in ni.inetAddresses.toList()) {
                    if (!a.isLoopbackAddress && a is Inet4Address) lista.add(ni.name + ": " + a.hostAddress)
                }
            }
        } catch (e: Exception) {
        }
        return lista.joinToString("\n")
    }

    private fun sim(v: Boolean) = if (v) "ligado" else "DESLIGADO"

    private fun atualizar() {
        status.text = "Servidor: " + sim(JarvisService.rodando) + " (porta " + JarvisService.PORTA + ")\n" +
            "Acessibilidade: " + sim(JarvisAccess.instance != null) + "\n" +
            "Notificações: " + sim(JarvisNotifs.instance != null) + "\n" +
            "Sobre outros apps: " + sim(Settings.canDrawOverlays(this)) + "\n" +
            "Brilho: " + sim(Settings.System.canWrite(this)) + "\n\n" +
            "Endereços do celular:\n" + ips()
    }
}
