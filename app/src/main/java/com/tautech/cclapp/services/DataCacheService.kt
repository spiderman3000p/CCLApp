package com.tautech.cclapp.services

import android.content.Context
import android.database.sqlite.SQLiteAccessPermException
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.util.Log
import com.tautech.cclapp.database.AppDatabase
import com.tautech.cclapp.interfaces.CclDataService

class DataCacheService {
    companion object{
        val TAG = "DATA_CACHE_SERVICE"
        var tables: List<Any>? = listOf()
        var db: AppDatabase? = null
        fun init(context: Context){
            try {
                db = AppDatabase.getDatabase(context)
            } catch(ex: SQLiteDatabaseLockedException) {
                Log.e(TAG, "Database error found", ex)
            } catch (ex: SQLiteAccessPermException) {
                Log.e(TAG, "Database error found", ex)
            } catch (ex: SQLiteCantOpenDatabaseException) {
                Log.e(TAG, "Database error found", ex)
            }
        }
        fun checkTableLastUpdate(tableName: String, context: Context) {

        }
    }
}