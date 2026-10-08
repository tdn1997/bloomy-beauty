package com.example.bloomybeauty.data.auth

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteException
import com.example.bloomybeauty.data.auth.AuthContract.Session
import com.example.bloomybeauty.data.auth.AuthContract.Users
import com.example.bloomybeauty.data.catalog.CatalogSchema
import com.example.bloomybeauty.data.cart.CartSchema
import com.example.bloomybeauty.data.order.OrderSchema

class AuthDatabaseHelper(
    context: Context,
    private val databaseName: String = AuthContract.DATABASE_NAME,
) : SQLiteOpenHelper(context.applicationContext, databaseName, null, AuthContract.DATABASE_VERSION) {
    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (databaseName == AuthContract.DATABASE_NAME) com.example.bloomybeauty.data.admin.DemoAdmin.seed(db)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE ${Users.TABLE} (
                ${Users.ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${Users.NAME} TEXT NOT NULL,
                ${Users.EMAIL} TEXT NOT NULL UNIQUE,
                ${Users.PASSWORD_HASH} BLOB NOT NULL,
                ${Users.PASSWORD_SALT} BLOB NOT NULL,
                ${Users.PASSWORD_ITERATIONS} INTEGER NOT NULL CHECK (${Users.PASSWORD_ITERATIONS} > 0)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE ${Session.TABLE} (
                ${Session.ID} INTEGER PRIMARY KEY CHECK (${Session.ID} = 1),
                ${Session.USER_ID} INTEGER NOT NULL REFERENCES ${Users.TABLE}(${Users.ID}) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        CatalogSchema.create(db)
        CartSchema.upgradeFromCatalog(db)
        OrderSchema.create(db)
        com.example.bloomybeauty.data.admin.AdminSchema.upgrade(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (newVersion > AuthContract.DATABASE_VERSION || oldVersion !in 1..4) {
            throw SQLiteException("Missing database migration from $oldVersion to $newVersion")
        }
        if (oldVersion < 2) CatalogSchema.create(db)
        if (oldVersion < 3 && newVersion >= 3) CartSchema.upgradeFromCatalog(db)
        if (oldVersion < 4 && newVersion >= 4) OrderSchema.create(db)
        if (oldVersion < 5 && newVersion >= 5) com.example.bloomybeauty.data.admin.AdminSchema.upgrade(db)
    }
}
