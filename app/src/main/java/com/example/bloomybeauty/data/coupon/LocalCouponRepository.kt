package com.example.bloomybeauty.data.coupon

import android.database.sqlite.SQLiteDatabase
import com.example.bloomybeauty.BuildConfig

object CouponSchema {
    fun upgrade(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE coupon_rules (
            code TEXT PRIMARY KEY, kind TEXT NOT NULL CHECK(kind IN ('FIXED','PERCENT')),
            value INTEGER NOT NULL CHECK(value > 0 AND (kind != 'PERCENT' OR value <= 100)),
            minimum_subtotal INTEGER NOT NULL CHECK(minimum_subtotal >= 0),
            maximum_discount INTEGER CHECK(maximum_discount >= 0),
            starts_at INTEGER NOT NULL, ends_at INTEGER NOT NULL CHECK(ends_at > starts_at),
            active INTEGER NOT NULL CHECK(active IN (0,1)), revision INTEGER NOT NULL DEFAULT 0 CHECK(revision >= 0)
        )""")
        db.execSQL("ALTER TABLE orders ADD COLUMN discount_vnd INTEGER NOT NULL DEFAULT 0 CHECK(discount_vnd >= 0)")
        db.execSQL("ALTER TABLE orders ADD COLUMN coupon_code TEXT")
        db.execSQL("ALTER TABLE orders ADD COLUMN coupon_rule_snapshot TEXT")
        if (BuildConfig.LOCAL_COUPONS_ENABLED) {
            val now = System.currentTimeMillis()
            val end = Math.addExact(now,30L*24*60*60*1000)
            db.execSQL("INSERT INTO coupon_rules VALUES('DEMO20','PERCENT',20,200000,50000,?,?,1,0)",arrayOf(now,end))
            db.execSQL("INSERT INTO coupon_rules VALUES('DEMO50K','FIXED',50000,500000,50000,?,?,1,0)",arrayOf(now,end))
        }
    }
}

// Only OrderRepository calls this inside its authenticated SQLite transaction.
class LocalCouponRepository(val enabled: Boolean = BuildConfig.LOCAL_COUPONS_ENABLED,
    private val clock: () -> Long = System::currentTimeMillis) {
    fun quote(db: SQLiteDatabase, code: String, subtotal: Long): CouponQuote? {
        val normalized = normalizeCoupon(code)
        if (normalized.isEmpty()) return null
        if (!enabled) throw CouponException(CouponError.DISABLED)
        val rule = list(db).firstOrNull {it.code == normalized} ?: throw CouponException(CouponError.NOT_FOUND)
        return rule.quote(subtotal,clock())
    }
    fun list(db: SQLiteDatabase): List<CouponRule> {
        if (!enabled) return emptyList()
        return db.rawQuery("SELECT code,kind,value,minimum_subtotal,maximum_discount,starts_at,ends_at,active,revision FROM coupon_rules ORDER BY code",null).use {c -> buildList {
            while(c.moveToNext()) add(CouponRule(c.getString(0),CouponKind.valueOf(c.getString(1)),c.getLong(2),c.getLong(3),
                if(c.isNull(4)) null else c.getLong(4),c.getLong(5),c.getLong(6),c.getInt(7)==1,c.getLong(8)))
        } }
    }
}
