package com.example.bloomybeauty.notifications

import com.example.bloomybeauty.BuildConfig
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class BloomyMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        if (BuildConfig.FIREBASE_ENABLED) PushNotifications(this).saveToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (!BuildConfig.FIREBASE_ENABLED) return
        val push = PushMessage.from(message.notification?.title, message.notification?.body, message.data)
            ?: return
        PushNotifications(this).show(push)
    }
}
