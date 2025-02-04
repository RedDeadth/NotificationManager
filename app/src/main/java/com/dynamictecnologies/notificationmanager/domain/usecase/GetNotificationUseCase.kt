package com.dynamictecnologies.notificationmanager.domain.usecase

import com.dynamictecnologies.notificationmanager.data.repository.NotificationRepository
import com.dynamictecnologies.notificationmanager.domain.model.Notification
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetNotificationsUseCase @Inject constructor(
    private val repository: NotificationRepository
) {
    operator fun invoke(): Flow<List<Notification>> {
        return repository.getAllNotifications()
    }
}