package com.example.bloomybeauty.data.catalog

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.example.bloomybeauty.data.auth.AuthContract

object CatalogSchema {
    fun create(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE categories (id TEXT PRIMARY KEY, name TEXT NOT NULL, sort_order INTEGER NOT NULL)")
        db.execSQL(
            """CREATE TABLE products (
                id TEXT PRIMARY KEY, name TEXT NOT NULL, brand TEXT NOT NULL,
                category_id TEXT NOT NULL REFERENCES categories(id), volume TEXT NOT NULL,
                price_vnd INTEGER NOT NULL CHECK (price_vnd >= 0), image_key TEXT NOT NULL,
                description TEXT NOT NULL, ingredients TEXT NOT NULL, usage TEXT NOT NULL,
                source_url TEXT NOT NULL, sort_order INTEGER NOT NULL
            )""".trimIndent(),
        )
        db.execSQL(
            """CREATE TABLE favorites (
                user_id INTEGER NOT NULL REFERENCES ${AuthContract.Users.TABLE}(${AuthContract.Users.ID}) ON DELETE CASCADE,
                product_id TEXT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
                PRIMARY KEY (user_id, product_id)
            )""".trimIndent(),
        )
        CatalogSeed.categories.forEachIndexed { index, category ->
            db.insertOrThrow("categories", null, ContentValues().apply {
                put("id", category.id); put("name", category.name); put("sort_order", index)
            })
        }
        CatalogSeed.products.forEachIndexed { index, product ->
            db.insertOrThrow("products", null, ContentValues().apply {
                put("id", product.id); put("name", product.name); put("brand", product.brand)
                put("category_id", product.categoryId); put("volume", product.volume)
                put("price_vnd", product.priceVnd); put("image_key", product.imageKey)
                put("description", product.description); put("ingredients", product.ingredients)
                put("usage", product.usage); put("source_url", product.sourceUrl); put("sort_order", index)
            })
        }
    }
}
