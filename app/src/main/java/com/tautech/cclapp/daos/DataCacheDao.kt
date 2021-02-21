package com.tautech.cclapp.daos

import androidx.room.*
import com.tautech.cclapp.models.CachedData
import com.tautech.cclapp.models.Certification

@Dao
interface DataCacheDao {
    @Query("SELECT * FROM cacheddata")
    fun getAll(): List<CachedData>

    @Query("SELECT * FROM cacheddata WHERE user_id = CAST(:userId as NUMERIC)")
    fun getAllByUser(userId: Long): List<CachedData>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(data: CachedData)

    @Update
    fun update(cachedData: CachedData)

    @Delete
    fun delete(cachedData: CachedData)
}