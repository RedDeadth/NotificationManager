package com.dynamictecnologies.notificationmanager.data.mapper

import com.dynamictecnologies.notificationmanager.data.model.UserInfo
import com.dynamictecnologies.notificationmanager.domain.entities.UserProfile

/**
 * Mapper para convertir entre UserInfo (data) y UserProfile (domain).
 * Principios: SRP, Clean Architecture
 */
object UserProfileMapper {
    
    fun toDomain(userInfo: UserInfo): UserProfile {
        return UserProfile(
            uid = userInfo.uid,
            username = userInfo.username,
            email = userInfo.email ?: "",
            createdAt = userInfo.createdAt
        )
    }
    
    fun toData(userProfile: UserProfile): UserInfo {
        return UserInfo(
            uid = userProfile.uid,
            username = userProfile.username,
            email = userProfile.email,
            createdAt = userProfile.createdAt
        )
    }
}
