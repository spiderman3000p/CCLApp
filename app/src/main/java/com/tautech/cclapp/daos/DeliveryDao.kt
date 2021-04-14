package com.tautech.cclapp.daos

import androidx.room.*
import com.tautech.cclapp.models.Delivery
import com.tautech.cclapp.models.DeliveryLine

@Dao
interface DeliveryDao {
    @Query("SELECT * FROM delivery")
    fun getAll(): List<Delivery>

    @Query("SELECT * FROM delivery WHERE orderDate = :date")
    fun getAll(date: String): List<Delivery>

    @Query("SELECT * FROM deliveryline WHERE deliveryId = CAST(:deliveryId AS NUMERIC)")
    fun getLines(deliveryId: Long): List<DeliveryLine>

    @Query("SELECT * FROM deliveryline WHERE deliveryId = CAST(:deliveryId AS NUMERIC) GROUP BY id")
    fun getGroupedLines(deliveryId: Long): List<DeliveryLine>

    @Query("SELECT * FROM delivery WHERE planificationId = CAST(:planificationId AS NUMERIC)")
    fun getAllByPlanification(planificationId: Long): List<Delivery>

    @Query("SELECT * FROM delivery WHERE deliveryid IN (:deliveryIds)")
    fun loadAllByIds(deliveryIds: LongArray): List<Delivery>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(delivery: Delivery)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(deliveries: MutableList<Delivery>)

    @Update()
    fun update(delivery: Delivery)

    @Delete
    fun delete(delivery: Delivery)

    @Query("DELETE FROM delivery")
    fun deleteAll()

    @Query("SELECT * FROM delivery WHERE deliveryid = CAST(:id AS NUMERIC)")
    fun getById(id: Long?): Delivery
}