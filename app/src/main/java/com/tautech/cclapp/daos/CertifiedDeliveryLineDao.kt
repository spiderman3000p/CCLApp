package com.tautech.cclapp.daos

import androidx.room.*
import com.tautech.cclapp.models.Certification

@Dao
interface CertifiedDeliveryLineDao {
    @Query("SELECT * FROM certification")
    fun getAll(): List<Certification>

    @Query("SELECT * FROM certification WHERE planificationId = CAST(:planificationId AS NUMERIC)")
    fun getAllByPlanification(planificationId: Int): List<Certification>

    @Query("SELECT * FROM certification WHERE deliveryLineId = CAST(:deliveryLineId AS NUMERIC)")
    fun get(deliveryLineId: Int): Certification

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(certification: Certification)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(planificationCertifications: List<Certification>)

    @Update
    fun update(certification: Certification)

    @Delete
    fun delete(certification: Certification)

    @Query("DELETE FROM certification WHERE planificationId = CAST(:planificationId AS NUMERIC)")
    fun deleteAllByPlanification(planificationId: Long)
}