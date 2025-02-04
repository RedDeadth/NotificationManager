package com.dynamictecnologies.notificationmanager.domain.model

data class Notification(
    val id: Int = 0,
    val packageName: String,
    val notificationText: String
)