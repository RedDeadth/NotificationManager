package com.dynamictecnologies.notificationmanager.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dynamictecnologies.notificationmanager.data.local.dao.NotificationDao
import com.dynamictecnologies.notificationmanager.data.local.entities.NotificationEntity

@Database(entities = [NotificationEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
}