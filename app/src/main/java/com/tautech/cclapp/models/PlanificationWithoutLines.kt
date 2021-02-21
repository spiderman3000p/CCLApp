package com.tautech.cclapp.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import org.json.JSONObject
import java.io.Serializable
@Entity
data class PlanificationWithoutLine(
    @PrimaryKey
    @SerializedName("planificationId")
    var id: Long,
    @ColumnInfo(name = "driverName")
    @SerializedName("driverName")
    var driverName: String = "",
    @ColumnInfo(name = "driverPhone")
    @SerializedName("driverPhone")
    var driverPhone: String = "",
    @ColumnInfo(name = "driverId")
    @SerializedName("driverId")
    var driverId: Long = 0,
    @ColumnInfo(name = "planificationDate")
    @SerializedName("planificationDate")
    var date: String = "",
    @ColumnInfo(name = "planificationState")
    @SerializedName("planificationState")
    var state: String = "",
    @ColumnInfo(name = "planificationType")
    @SerializedName("planificationType")
    var type: String = "",
    @ColumnInfo(name = "totalDelivery")
    @SerializedName("totalDelivery")
    var totalDelivery: Long = 0,
    @ColumnInfo(name = "totalQuantity")
    @SerializedName("totalQuantity")
    var totalQuantity: Long = 0,
    @ColumnInfo(name = "totalWeight")
    @SerializedName("totalWeight")
    var totalWeight: Double = 0.0,
    @ColumnInfo(name = "vehicleId")
    @SerializedName("vehicleId")
    var vehicleId: Long = 0,
    @ColumnInfo(name = "licensePlate")
    @SerializedName("licensePlate")
    var vehicleLicensePlate: String = "",
    @ColumnInfo(name = "vehicleType")
    @SerializedName("vehicleType")
    var vehicleType: String = ""): Serializable {

}