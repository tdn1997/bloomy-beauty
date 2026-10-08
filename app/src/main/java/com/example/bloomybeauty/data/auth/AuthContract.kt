package com.example.bloomybeauty.data.auth

import android.provider.BaseColumns

object AuthContract {
    const val DATABASE_NAME = "bloomy_beauty.db"
    const val DATABASE_VERSION = 5

    object Users {
        const val TABLE = "users"
        const val ID = BaseColumns._ID
        const val NAME = "name"
        const val EMAIL = "email"
        const val PASSWORD_HASH = "password_hash"
        const val PASSWORD_SALT = "password_salt"
        const val PASSWORD_ITERATIONS = "password_iterations"
    }

    object Session {
        const val TABLE = "auth_session"
        const val ID = BaseColumns._ID
        const val USER_ID = "user_id"
    }
}
