package com.rodin.messages

import android.app.Application
import com.rodin.messages.notify.Notifier

class MessagesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
    }
}
