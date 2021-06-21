package com.tautech.cclapp.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Entity
data class Planification(
    @PrimaryKey
    @SerializedName("planificationId")
    var id: Long,
    @SerializedName("label")
    @ColumnInfo(name = "label")
    var label: String? = "",
    var legalized: Boolean? = false,
    @SerializedName("totalCertified")
    @ColumnInfo(name = "totalCertified")
    var totalCertified: Int? = 0,
    @SerializedName("customerId")
    @ColumnInfo(name = "customerId")
    var customerId: Long? = 0,
    @SerializedName("driverId")
    @ColumnInfo(name = "driverId")
    var driverId: Long? = 0,
    @SerializedName("dispatchDate")
    @ColumnInfo(name = "dispatchDate")
    var dispatchDate: String? = "",
    @SerializedName("planificationState")
    @ColumnInfo(name = "planificationState")
    var state: String? = "",
    @SerializedName("planificationType")
    @ColumnInfo(name = "planificationType")
    var planificationType: String? = "",
    @SerializedName("totalDeliveries")
    @ColumnInfo(name = "totalDeliveries")
    var totalDeliveries: Int? = 0,
    @SerializedName("totalDelivered")
    @ColumnInfo(name = "totalDelivered")
    var totalDelivered: Int? = 0,
    @SerializedName("totalPartial")
    @ColumnInfo(name = "totalPartial")
    var totalPartial: Int? = 0,
    @SerializedName("totalUndelivered")
    @ColumnInfo(name = "totalUndelivered")
    var totalUndelivered: Int? = 0,
    @SerializedName("totalLines")
    @ColumnInfo(name = "totalLines")
    var totalLines: Int? = 0,
    @SerializedName("totalDeliveredLines")
    @ColumnInfo(name = "totalDeliveredLines")
    var totalDeliveredLines: Int? = 0,
    @SerializedName("totalUnits")
    @ColumnInfo(name = "totalUnits")
    var totalUnits: Int? = 0,
    @SerializedName("totalDeliveredUnits")
    @ColumnInfo(name = "totalDeliveredUnits")
    var totalDeliveredUnits: Int? = 0,
    @SerializedName("totalValue")
    @ColumnInfo(name = "totalValue")
    var totalValue: Double? = 0.0,
    @SerializedName("totalVolume")
    @ColumnInfo(name = "totalVolume")
    var totalVolume: Double? = 0.0,
    @SerializedName("totalWeight")
    @ColumnInfo(name = "totalWeight")
    var totalWeight: Double? = 0.0,
    @SerializedName("licensePlate")
    @ColumnInfo(name = "licensePlate")
    var licensePlate: String? = ""): Serializable {

}