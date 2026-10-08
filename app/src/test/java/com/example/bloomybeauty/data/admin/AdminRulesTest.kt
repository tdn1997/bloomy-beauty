package com.example.bloomybeauty.data.admin

import com.example.bloomybeauty.data.catalog.Product
import org.junit.Assert.*
import org.junit.Test

class AdminRulesTest {
    private val valid = Product("test","Mỹ phẩm","skin","100 ml",120000,"product_cleanser","Mô tả","Thành phần","Cách dùng","https://example.com/product")
    @Test fun validateIntegerMoneyStockImageAndRequiredFields() {
        assertTrue(AdminRules.valid(valid))
        listOf(valid.copy(priceVnd=0), valid.copy(stock=-1), valid.copy(name=" "), valid.copy(imageKey="unknown"),valid.copy(ingredients="")).forEach {assertFalse(AdminRules.valid(it))}
        assertTrue(AdminRules.valid(valid.copy(stock=0,sourceUrl="")))
    }
    @Test fun onlyAcceptHttpsSources() {
        listOf("http://example.com","javascript:alert(1)","https:","https://").forEach {assertFalse(AdminRules.valid(valid.copy(sourceUrl=it)))}
    }
    @Test fun enforceTerminalStatesAndShippingCancellationRule() {
        assertEquals(listOf("CONFIRMED","CANCELLED"),AdminRules.transitions["PENDING"])
        assertEquals(listOf("SHIPPING","CANCELLED"),AdminRules.transitions["CONFIRMED"])
        assertEquals(listOf("DELIVERED"),AdminRules.transitions["SHIPPING"])
        assertNull(AdminRules.transitions["DELIVERED"]);assertNull(AdminRules.transitions["CANCELLED"])
    }
}
