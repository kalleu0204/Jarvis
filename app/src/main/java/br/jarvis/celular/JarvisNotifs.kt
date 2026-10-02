package br.jarvis.celular

import android.service.notification.NotificationListenerService

class JarvisNotifs : NotificationListenerService() {
    companion object {
        @Volatile
        var instance: JarvisNotifs? = null
    }

    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        instance = null
    }
}
