package br.jarvis.celular

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccess : AccessibilityService() {
    companion object {
        @Volatile
        var instance: JarvisAccess? = null
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    private fun coletar(no: AccessibilityNodeInfo, saida: MutableList<AccessibilityNodeInfo>) {
        saida.add(no)
        for (i in 0 until no.childCount) {
            val filho = no.getChild(i)
            if (filho != null) coletar(filho, saida)
        }
    }

    private fun casa(texto: CharSequence?, chave: String, exato: Boolean): Boolean {
        if (texto == null) return false
        val t = norm(texto.toString())
        val k = norm(chave)
        if (t.isEmpty() || k.isEmpty()) return false
        return if (exato) (t == k || t.startsWith("$k ")) else t.contains(k)
    }

    fun achar(ids: List<String>, textos: List<String>, exato: Boolean): AccessibilityNodeInfo? {
        val raiz = rootInActiveWindow ?: return null
        for (id in ids) {
            val lista = raiz.findAccessibilityNodeInfosByViewId(id)
            if (lista != null && lista.isNotEmpty()) return lista[0]
        }
        val todos = mutableListOf<AccessibilityNodeInfo>()
        coletar(raiz, todos)
        for (n in todos) {
            for (chave in textos) {
                if (casa(n.text, chave, exato) || casa(n.contentDescription, chave, exato)) return n
            }
        }
        return null
    }

    fun clicar(no: AccessibilityNodeInfo): Boolean {
        var atual: AccessibilityNodeInfo? = no
        while (atual != null) {
            if (atual.isClickable && atual.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            atual = atual.parent
        }
        val r = Rect()
        no.getBoundsInScreen(r)
        return tocar(r.centerX().toFloat(), r.centerY().toFloat())
    }

    fun esperarEClicar(ids: List<String>, textos: List<String>, ms: Long, exato: Boolean = true): Boolean {
        val fim = SystemClock.uptimeMillis() + ms
        while (SystemClock.uptimeMillis() < fim) {
            val no = achar(ids, textos, exato)
            if (no != null && clicar(no)) return true
            Thread.sleep(500)
        }
        return false
    }

    fun tocar(x: Float, y: Float): Boolean {
        val caminho = Path()
        caminho.moveTo(x, y)
        val gesto = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(caminho, 0, 60))
            .build()
        return dispatchGesture(gesto, null, null)
    }

    fun digitar(texto: String): Boolean {
        val raiz = rootInActiveWindow ?: return false
        val campo = raiz.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, texto)
        return campo.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun lerTela(): String {
        val raiz = rootInActiveWindow ?: return ""
        val todos = mutableListOf<AccessibilityNodeInfo>()
        coletar(raiz, todos)
        val vistos = LinkedHashSet<String>()
        for (n in todos) {
            val bruto: CharSequence? = n.text ?: n.contentDescription
            val t = bruto?.toString()?.trim() ?: ""
            if (t.length in 2..80) vistos.add(t)
            if (vistos.size >= 25) break
        }
        return vistos.joinToString(", ")
    }
}
