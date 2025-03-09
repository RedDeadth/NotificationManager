package com.dynamictecnologies.notificationmanager.data.model

data class UserInfo(
    val uid: String = "",
    val username: String = "",
    val email: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val sharedWith: Map<String, Boolean> = emptyMap()

) {
    fun toMap(): Map<String, Any> = mapOf(
        "uid" to uid,
        "username" to username,
        "email" to (email ?: ""),
        "createdAt" to createdAt,
        "sharedWith" to sharedWith
    )
}