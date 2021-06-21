package com.tautech.cclapp.daos

import androidx.room.*
import com.tautech.cclapp.models.PlanificationPaymentDetail

@Dao
interface PlanificationPaymentDetailDao {
    @Query("SELECT * FROM planificationpaymentdetail")
    fun getAll(): List<PlanificationPaymentDetail>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(paymentDetail: PlanificationPaymentDetail)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(paymentDetails: MutableList<PlanificationPaymentDetail>)

    @Update()
    fun update(planificationPaymentDetail: PlanificationPaymentDetail)

    @Delete
    fun delete(planificationPaymentDetail: PlanificationPaymentDetail)

    @Query("DELETE FROM planificationpaymentdetail")
    fun deleteAll()

    @Query("SELECT * FROM planificationpaymentdetail WHERE code = :code")
    fun getByCode(code: String): PlanificationPaymentDetail
}