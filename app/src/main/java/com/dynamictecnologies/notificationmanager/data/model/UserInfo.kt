package com.dynamictecnologies.notificationmanager.data.model

data class UserInfo(
    val uid: String = "",
    val username: String = "",
    val email: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val sharedWith: Map<String, String> = emptyMap()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "uid" to uid,
        "email" to (email ?: ""),
        "createdAt" to createdAt,
        "sharedWith" to sharedWith
    )
}