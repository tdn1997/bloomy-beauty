package com.example.bloomybeauty.notifications

import org.junit.Assert.*
import org.junit.Test

class PushMessageTest {
    @Test fun notificationPayloadTakesPrecedenceOverData() {
        assertEquals(PushMessage("Đơn hàng", "Đang giao"),
            PushMessage.from("Đơn hàng", "Đang giao", mapOf("title" to "Khác", "body" to "Khác")))
    }
    @Test fun dataOnlyMessageIsDisplayed() {
        assertEquals(PushMessage("Ưu đãi", "Giảm giá hôm nay"),
            PushMessage.from(null, null, mapOf("title" to "Ưu đãi", "body" to "Giảm giá hôm nay")))
    }
    @Test fun blankNotificationFieldsFallBackToData() {
        assertEquals(PushMessage("Tin mới", "Nội dung"),
            PushMessage.from(" ", "", mapOf("title" to "Tin mới", "body" to "Nội dung")))
    }
    @Test fun missingTitleUsesBrand() {
        assertEquals(PushMessage("Bloomy Beauty", "Xin chào"),
            PushMessage.from(null, "Xin chào", emptyMap()))
    }
    @Test fun titleOnlyMessageIsDisplayed() {
        assertEquals(PushMessage("Tin mới", ""), PushMessage.from("Tin mới", null, emptyMap()))
    }
    @Test fun emptyAndUnrelatedDataAreIgnored() {
        assertNull(PushMessage.from(null, null, emptyMap()))
        assertNull(PushMessage.from(" ", "", mapOf("orderId" to "123", "body" to " ")))
    }
}
