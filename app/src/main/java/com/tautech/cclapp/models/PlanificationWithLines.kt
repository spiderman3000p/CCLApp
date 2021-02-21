package com.tautech.cclapp.models

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class PlanificationWithLine(
    @SerializedName("planificationId")
    var id: Long,
    @SerializedName("driverName")
    var driverName: String = "",
    @SerializedName("driverPhone")
    var driverPhone: String = "",
    @SerializedName("driverId")
    var driverId: Long = 0,
    @SerializedName("planificationDate")
    var date: String = "",
    @SerializedName("planificationState")
    var state: String = "",
    @SerializedName("planificationType")
    var type: String = "",
    @SerializedName("totalDelivery")
    var totalDelivery: Long = 0,
    @SerializedName("totalQuantity")
    var totalQuantity: Long = 0,
    @SerializedName("totalWeight")
    var totalWeight: Double = 0.0,
    @SerializedName("vehicleId")
    var vehicleId: Long = 0,
    @SerializedName("licensePlate")
    var vehicleLicensePlate: String = "",
    @SerializedName("vehicleType")
    var vehicleType: String = "",
    @SerializedName("deliveryMap")
    var deliveryMap: HashMap<String, PlanificationLine> = hashMapOf(),
    @SerializedName("deliveredStats")
    var deliveredStats: DeliveredStats?): Serializable {

}