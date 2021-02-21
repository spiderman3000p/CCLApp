package com.tautech.cclapp.models

import androidx.room.ColumnInfo
import androidx.room.Embedded
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
    @SerializedName("address")
    @ColumnInfo(name = "address")
    var address: String? = "",
    @SerializedName("totalCertificate")
    @ColumnInfo(name = "totalCertificate")
    var totalCertificate: Int? = 0,
    @SerializedName("customerAddressId")
    @ColumnInfo(name = "customerAddressId")
    var customerAddressId: Long? = 0,
    @SerializedName("customerId")
    @ColumnInfo(name = "customerId")
    var customerId: Long? = 0,
    @SerializedName("customerAddressLatitude")
    @ColumnInfo(name = "customerAddressLatitude")
    var customerAddressLatitude: Double? = 0.0,
    @SerializedName("customerAddressLongitude")
    @ColumnInfo(name = "customerAddressLongitude")
    var customerAddressLongitude: Double? = 0.0,
    @SerializedName("customerName")
    @ColumnInfo(name = "customerName")
    var customerName: String? = "",
    @SerializedName("driverName")
    @ColumnInfo(name = "driverName")
    var driverName: String? = "",
    @SerializedName("driverId")
    @ColumnInfo(name = "driverId")
    var driverId: Long? = 0,
    @SerializedName("planificationDate")
    @ColumnInfo(name = "planificationDate")
    var date: String? = "",
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
    @SerializedName("totalLines")
    @ColumnInfo(name = "totalLines")
    var totalLines: Int? = 0,
    @SerializedName("totalUnits")
    @ColumnInfo(name = "totalUnits")
    var totalUnits: Int? = 0,
    @SerializedName("totalValue")
    @ColumnInfo(name = "totalValue")
    var totalValue: Double? = 0.0,
    @SerializedName("totalVolume")
    @ColumnInfo(name = "totalVolume")
    var totalVolume: Double? = 0.0,
    @SerializedName("totalWeight")
    @ColumnInfo(name = "totalWeight")
    var totalWeight: Double? = 0.0,
    @SerializedName("vehicleId")
    @ColumnInfo(name = "vehicleId")
    var vehicleId: Long? = 0,
    @SerializedName("vehicleLicensePlate")
    @ColumnInfo(name = "vehicleLicensePlate")
    var vehicleLicensePlate: String? = "",
    @SerializedName("vehicleType")
    @ColumnInfo(name = "vehicleType")
    var vehicleType: String? = "",
    @SerializedName("vehicleTypeMaxCapacity")
    @ColumnInfo(name = "vehicleTypeMaxCapacity")
    var vehicleTypeMaxCapacity: Double? = 0.0,
    @SerializedName("vehicleTypeMaxDeliveries")
    @ColumnInfo(name = "vehicleTypeMaxDeliveries")
    var vehicleTypeMaxDeliveries: Int? = 0,
    @SerializedName("vehicleTypeMaxValue")
    @ColumnInfo(name = "vehicleTypeMaxValue")
    var vehicleTypeMaxValue: Double? = 0.0,
    @SerializedName("vehicleTypeMaxWeight")
    @ColumnInfo(name = "vehicleTypeMaxWeight")
    var vehicleTypeMaxWeight: Double? = 0.0,
    @Embedded
    @SerializedName("deliveredStats")
    var deliveredStats: DeliveredStats?): Serializable {

}