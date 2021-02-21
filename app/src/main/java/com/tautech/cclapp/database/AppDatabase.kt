package com.tautech.cclapp.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tautech.cclapp.daos.*
import com.tautech.cclapp.models.*
import org.joda.time.DateTime
import java.util.*
import kotlin.math.abs

@Database(entities = arrayOf(Planification::class, DeliveryLine::class, CachedData::class,
    PlanificationWithoutLine::class, PlanificationLine::class, Certification::class, UserInformation::class,
    PendingToUploadCertification::class, StateFormDefinition::class, StateFormField::class), version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dataCacheDao(): DataCacheDao
    abstract fun userInfoDao(): UserInformationDao
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

        fun isExpiredChache(tableName: String, driverId: Long): Boolean{
            val allCachedData = this.INSTANCE?.dataCacheDao()?.getAllByUser(driverId)
            var lastDate: Date? = null
            if (!allCachedData.isNullOrEmpty()) {
                lastDate = Date(allCachedData.firstOrNull {
                    it.table == tableName
                }?.timestamp!!)
                val today = Date()
                val difference = abs(today.time - lastDate.time) / (60 * 60 * 1000)
                // si la ultima fecha es anterior a hoy o superior a una hora
                return lastDate.before(today) || difference > 1
            }
            return false
        }
    }
}