package com.tautech.cclapp.daos

import androidx.room.*
import com.tautech.cclapp.models.*

@Dao
interface DeliveryDao {
    @Query("SELECT * FROM planificationline")
    fun getAll(): List<PlanificationLine>

    @Query("SELECT * FROM planificationline WHERE deliveryDate = :date")
    fun getAll(date: String): List<PlanificationLine>

    @Query("SELECT * FROM deliveryline WHERE deliveryId = CAST(:deliveryId AS NUMERIC)")
    fun getLines(deliveryId: Long): List<DeliveryLine>

    @Query("SELECT * FROM deliveryline WHERE deliveryId = CAST(:deliveryId AS NUMERIC) GROUP BY deliveryLineId")
    fun getGroupedLines(deliveryId: Long): List<DeliveryLine>

    @Query("SELECT * FROM planificationline WHERE planificationId = CAST(:planificationId AS NUMERIC)")
    fun getAllByPlanification(planificationId: Int): List<PlanificationLine>

    @Query("SELECT * FROM planificationline WHERE id IN (:planificationLineIds)")
    fun loadAllByIds(planificationLineIds: IntArray): List<PlanificationLine>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(planificationLine: PlanificationLine)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(planificationLines: MutableList<PlanificationLine>)

    @Update()
    fun update(planificationLine: PlanificationLine)

    @Delete
    fun delete(planificationLine: PlanificationLine)

    @Query("DELETE FROM planificationline")
    fun deleteAll()

    @Query("SELECT * FROM planificationline WHERE id = :id")
    fun getById(id: Long?): PlanificationLine
}