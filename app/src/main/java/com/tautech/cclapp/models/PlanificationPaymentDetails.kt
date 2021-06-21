package com.tautech.cclapp.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class PaymentDetail(
    var value: Double,
    var count: Int): Serializable

data class PlanificationPaymentDetails(
    var cash: PaymentDetail? = null,
    var electronic: PaymentDetail? = null,
    var credit: PaymentDetail? = null,
    var returns: PaymentDetail? = null
): Serializable