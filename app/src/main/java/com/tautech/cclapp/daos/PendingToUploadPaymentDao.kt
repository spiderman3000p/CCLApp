package com.tautech.cclapp.daos

import androidx.room.*
import com.tautech.cclapp.models.PendingToUploadPayment

@Dao
interface PendingToUploadPaymentDao {
    @Query("SELECT * FROM pendingtouploadpayment")
    fun getAll(): List<PendingToUploadPayment>

    @Query("SELECT COUNT(*) FROM pendingtouploadpayment")
    fun count(): Long

    @Query("SELECT * FROM pendingtouploadpayment WHERE code = :code")
    fun getAllByCode(code: String): List<PendingToUploadPayment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(payment: PendingToUploadPayment)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(payment: List<PendingToUploadPayment>)

    @Update
    fun update(payment: PendingToUploadPayment)

    @Delete
    fun delete(payment: PendingToUploadPayment)

    @Query("DELETE FROM pendingtouploadpayment WHERE code IN (:codes)")
    fun deleteAllByCode(codes: List<String>)
}