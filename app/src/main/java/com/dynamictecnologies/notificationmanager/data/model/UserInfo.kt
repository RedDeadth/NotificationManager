package com.dynamictecnologies.notificationmanager.data.model

data class UserInfo(
    val uid: String,
    val username: String,
    val createdAt: Long = System.currentTimeMillis(),
    val sharedWith: List<String> = emptyList()
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "uid" to uid,
            "username" to username,
            "createdAt" to createdAt,
            "sharedWith" to sharedWith
        )
    }
}