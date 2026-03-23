package com.memorandum.data.local.room.enums

enum class HeartbeatFrequency(val intervalMinutes: Long) {
    LOW(120),
    MEDIUM(60),
    HIGH(30),
}
