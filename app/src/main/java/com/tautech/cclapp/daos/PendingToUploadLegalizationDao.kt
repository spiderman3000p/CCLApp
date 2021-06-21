package com.tautech.cclapp.daos

import androidx.room.*
import com.tautech.cclapp.models.PendingToUploadLegalization

@Dao
interface PendingToUploadLegalizationDao {
    @Query("SELECT * FROM pendingtouploadlegalization")
    fun getAll(): List<PendingToUploadLegalization>

    @Query("SELECT COUNT(*) FROM pendingtouploadlegalization")
    fun count(): Long

    @Query("SELECT * FROM pendingtouploadlegalization WHERE code = :code")
    fun getAllByCode(code: String): List<PendingToUploadLegalization>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(payment: PendingToUploadLegalization)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(payment: List<PendingToUploadLegalization>)

    @Update
    fun update(payment: PendingToUploadLegalization)

    @Delete
    fun delete(payment: PendingToUploadLegalization)

    @Query("DELETE FROM pendingtouploadlegalization WHERE code IN (:codes)")
    fun deleteAllByCode(codes: List<String>)
}