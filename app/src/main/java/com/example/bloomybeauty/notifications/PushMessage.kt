package com.example.bloomybeauty.notifications

data class PushMessage(val title: String, val body: String) {
    companion object {
        fun from(notificationTitle: String?, notificationBody: String?, data: Map<String, String>): PushMessage? {
            val title = notificationTitle?.takeIf { it.isNotBlank() }
                ?: data["title"]?.takeIf { it.isNotBlank() }
            val body = notificationBody?.takeIf { it.isNotBlank() }
                ?: data["body"]?.takeIf { it.isNotBlank() }
            if (title == null && body == null) return null
            return PushMessage(title ?: "Bloomy Beauty", body.orEmpty())
        }
    }
}
