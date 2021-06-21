package com.tautech.cclapp.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class PlanificationDetails(
    var planificationId: Long = 0,
    var deliveries: Int = 0,
    var delivered: Int = 0,
    var partial: Int = 0,
    var undelivered: Int = 0,
    var pending_deliveries: Int = 0,
    var incompleteLine: Int = 0,
    var completeLine: Int = 0,
    var totalLine: Int = 0,
    var cash: Double = 0.0,
    var credit: Double = 0.0,
    var returns: Double = 0.0,
    var electronic: Double = 0.0
): Serializable {

}