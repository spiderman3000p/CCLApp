package com.tautech.cclapp.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class PlanificationPaymentDetailsResponse(
    var cash: String? = null,
    var electronic: String? = null,
    var credit: String? = null,
    var returns: String? = null
): Serializable {

}