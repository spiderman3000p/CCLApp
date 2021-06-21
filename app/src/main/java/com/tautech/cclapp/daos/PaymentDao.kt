package com.tautech.cclapp.daos

import androidx.room.*
import com.tautech.cclapp.models.Payment
import com.tautech.cclapp.models.DeliveryLine

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payment")
    fun getAll(): List<Payment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(payment: Payment)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(deliveries: MutableList<Payment>)

    @Update()
    fun update(payment: Payment)

    @Delete
    fun delete(payment: Payment)

    @Query("DELETE FROM payment")
    fun deleteAll()

    @Query("SELECT * FROM payment WHERE id = CAST(:id AS NUMERIC)")
    fun getById(id: Long?): Payment
}