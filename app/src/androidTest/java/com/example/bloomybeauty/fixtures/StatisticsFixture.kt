package com.example.bloomybeauty.fixtures

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.example.bloomybeauty.data.statistics.StatisticsPeriod
import java.util.UUID

object StatisticsFixture {
    val period = StatisticsPeriod.parse("2026-11-08","2026-11-08")
    fun order(db: SQLiteDatabase, userId: Long, status: String, created: Long, delivered: Long?,
        subtotal: Long, discount: Long = 0, fee: Long = 0, product: String = "cleanser", quantity: Int = 1): Long {
        require(subtotal % quantity == 0L)
        val id = db.insertOrThrow("orders",null,ContentValues().apply {
            put("user_id",userId);put("request_id",UUID.randomUUID().toString());put("fingerprint","statistics-fixture")
            put("created_at",created);put("status",status);if(delivered == null) putNull("delivered_at") else put("delivered_at",delivered)
            put("recipient","Khách thống kê");put("phone","0912345678");put("address","123 Nguyễn Huệ, Thành phố Hồ Chí Minh")
            put("subtotal_vnd",subtotal);put("discount_vnd",discount);put("fee_vnd",fee);put("total_vnd",Math.addExact(subtotal-discount,fee))
            if(discount > 0) {put("coupon_code","FIXTURE");put("coupon_rule_snapshot","{}")}
        })
        db.insertOrThrow("order_items",null,ContentValues().apply {
            put("order_id",id);put("product_id",product);put("name","Tên sản phẩm khi đặt")
            put("volume","1 chai");put("image_key","product_cleanser");put("quantity",quantity);put("unit_price_vnd",subtotal/quantity)
        })
        return id
    }
    fun seed(db: SQLiteDatabase, userId: Long) {
        val start = period.startInclusive;val end = period.endExclusive;val outside = start-10L*86400000
        order(db,userId,"DELIVERED",outside,start,100_000,10_000,30_000,"cleanser",2)
        order(db,userId,"DELIVERED",outside,end-1,200_000,20_000,0,"serum",2)
        order(db,userId,"DELIVERED",start+1000,start-1,300_000,0,30_000,"cleanser",5)
        order(db,userId,"DELIVERED",start+1000,end,420_000,20_000,0,"serum",7)
        order(db,userId,"DELIVERED",start+1000,null,50_000,0,30_000,"serum",1)
        order(db,userId,"PENDING",start+1000,null,400_000,0,30_000,"cleanser",4)
        order(db,userId,"CONFIRMED",start+1000,null,600_000,0,0,"cleanser",2)
        order(db,userId,"SHIPPING",start+1000,null,300_000,0,30_000,"cleanser",3)
        order(db,userId,"CANCELLED",start+1000,null,450_000,0,30_000,"serum",9)
        db.execSQL("UPDATE products SET stock=30,active=1")
        db.execSQL("UPDATE products SET stock=5,name='Gel đã đổi tên',price_vnd=999999 WHERE id='cleanser'")
        db.execSQL("UPDATE products SET stock=0 WHERE id='serum'")
        db.execSQL("UPDATE products SET stock=2,active=0 WHERE id='toner'")
    }
}
