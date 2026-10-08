package com.example.bloomybeauty.feature.order

fun orderStatusLabel(status: String): String = when(status) {
    "PENDING" -> "Chờ xác nhận"
    "CONFIRMED" -> "Đã xác nhận"
    "SHIPPING" -> "Đang giao"
    "DELIVERED" -> "Đã giao"
    "CANCELLED" -> "Đã hủy"
    else -> status
}
