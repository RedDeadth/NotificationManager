package com.dynamictecnologies.notificationmanager.data.model

data class UserInfo(
    val uid: String = "",
    val username: String = "",
    val email: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val sharedWith: Map<String, String> = emptyMap(),
    val isShared: Boolean = false
)