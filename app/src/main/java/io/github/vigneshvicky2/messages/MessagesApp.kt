package io.github.vigneshvicky2.messages

import android.app.Application
import io.github.vigneshvicky2.messages.notify.Notifier

class MessagesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        io.github.vigneshvicky2.messages.data.Demo.init(this)
    }
}
