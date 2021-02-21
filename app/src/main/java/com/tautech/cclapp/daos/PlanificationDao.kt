package com.tautech.cclapp.daos

import androidx.room.*
import com.tautech.cclapp.models.Certification
import com.tautech.cclapp.models.Planification

@Dao
interface PlanificationDao {
    @Query("SELECT * FROM planification ORDER BY planificationDate DESC")
    fun getAll(): List<Planification>

    @Query("SELECT * FROM planification WHERE planificationDate = :date ORDER BY planificationDate DESC")
    fun getAll(date: String): List<Planification>

    @Query("SELECT * FROM planification WHERE planificationType = :type AND driverId = CAST(:driverId AS NUMERIC) ORDER BY planificationDate DESC")
    fun getAllByTypeAndDriver(type: String, driverId: Long?): List<Planification>

    @Query("SELECT * FROM planification WHERE id IN (:planificationIds) ORDER BY planificationDate DESC")
    fun loadAllByIds(planificationIds: IntArray): List<Planification>

    @Query("SELECT * FROM planification WHERE id = CAST(:id AS NUMERIC)")
    fun getById(id: Int): Planification

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(planification: Planification)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(planificationCertifications: List<Planification>)

    @Update
    fun update(planification: Planification?)

    @Delete
    fun delete(planification: Planification)

    @Query("UPDATE planification SET planificationState=:state WHERE id = CAST(:planificationId AS NUMERIC)")
    fun updateState(planificationId: Long?, state: String)
}