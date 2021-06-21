package com.tautech.cclapp.models

import androidx.room.*
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Entity(indices = [Index("planificationId")], foreignKeys = [ForeignKey(entity = Planification::class,
    parentColumns = ["id"],
    childColumns = ["planificationId"],
    onDelete = ForeignKey.CASCADE,
    onUpdate = ForeignKey.CASCADE)])
data class Delivery(
    @PrimaryKey
    @SerializedName("deliveryId")
    var deliveryId: Long = 0,
    @ColumnInfo(name = "planificationId")
    @SerializedName("planificationId")
    var planificationId: Long? = 0,
    @ColumnInfo(name = "receiverAddressLatitude")
    @SerializedName("receiverAddressLatitude")
    var receiverAddressLatitude: String? = "",
    @ColumnInfo(name = "receiverAddressLongitude")
    @SerializedName("receiverAddressLongitude")
    var receiverAddressLongitude: String? = "",
    @ColumnInfo(name = "orderDate")
    @SerializedName("orderDate")
    var orderDate: String? = "",
    @ColumnInfo(name = "deliveryState")
    @SerializedName("deliveryState")
    var deliveryState: String? = "",
    @ColumnInfo(name = "deliveryNumber")
    @SerializedName("deliveryNumber")
    var deliveryNumber: String = "",
    @ColumnInfo(name = "notes")
    @SerializedName("notes")
    var notes: String = "",
    @ColumnInfo(name = "receiverAddress")
    @SerializedName("receiverAddress")
    var receiverAddress: String? = "",
    @ColumnInfo(name = "receiverName")
    @SerializedName("receiverName")
    var receiverName: String? = "",
    @ColumnInfo(name = "receiverPhone")
    @SerializedName("receiverPhone")
    var receiverPhone: String? = "",
    @ColumnInfo(name = "referenceDocument")
    @SerializedName("referenceDocument")
    var referenceDocument: String? = "",
    @ColumnInfo(name = "senderAddress")
    @SerializedName("senderAddress")
    var senderAddress: String? = "",
    @ColumnInfo(name = "senderName")
    @SerializedName("senderName")
    var senderName: String? = "",
    @ColumnInfo(name = "totalValue")
    @SerializedName("totalValue")
    var totalValue: Double? = 0.0,
    @ColumnInfo(name = "totalWeight")
    @SerializedName("totalWeight")
    var totalWeight: Double? = 0.0,
    @ColumnInfo(name = "totalLines")
    @SerializedName("totalLines")
    var totalLines: Int? = 0,
    @ColumnInfo(name = "totalDeliveredLines")
    @SerializedName("totalDeliveredLines")
    var totalDeliveredLines: Int? = 0,
    @ColumnInfo(name = "totalQuantity")
    @SerializedName("totalQuantity")
    var totalQuantity: Int? = 0,
    @SerializedName("totalDelivered")
    @ColumnInfo(name = "totalDelivered")
    var totalDelivered: Int? = 0,
    @SerializedName("totalCertified")
    @ColumnInfo(name = "totalCertified")
    var totalCertified: Int? = 0
): Serializable