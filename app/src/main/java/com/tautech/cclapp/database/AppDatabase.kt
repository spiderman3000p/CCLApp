package com.tautech.cclapp.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tautech.cclapp.daos.*
import com.tautech.cclapp.models.*

@Database(entities = [Planification::class, DeliveryLine::class, Delivery::class, Certification::class, PendingToUploadCertification::class, StateFormDefinition::class, StateFormField::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun planificationDao(): PlanificationDao
    abstract fun deliveryLineDao(): DeliveryLineDao
    abstract fun deliveryDao(): DeliveryDao
    abstract fun certificationDao(): CertifiedDeliveryLineDao
    abstract fun pendingToUploadCertificationDao(): PendingToUploadCertificationDao
    abstract fun stateFormDefinitionDao(): StateFormDefinitionDao
    abstract fun stateFormFieldDao(): StateFormFieldDao
    companion object{
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cclexpress_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}