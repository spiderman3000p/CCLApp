package com.tautech.cclapp.models

import androidx.room.*
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Entity(primaryKeys = ["planificationId", "deliveryLineId", "deliveryId", "index"],
    indices = [Index(value = ["planificationId", "deliveryId", "index"], name = "delivery_line_index", unique = true)],
    foreignKeys = [ForeignKey(entity = PlanificationLine::class,
        parentColumns = ["id"],
        childColumns = ["deliveryId"],
        onDelete = ForeignKey.CASCADE,
        onUpdate = ForeignKey.CASCADE)])
data class DeliveryLine(
    @SerializedName("deliveryLineId")
    @ColumnInfo(name = "deliveryLineId")
    var id: Long = 0,
    @SerializedName("packetType")
    @ColumnInfo(name = "packetType")
    var packetType: String = "",
    @SerializedName("price")
    @ColumnInfo(name = "price")
    var price: Double = 0.0,
    @SerializedName("quantity")
    @ColumnInfo(name = "quantity")
    var quantity: Int = 0,
    @SerializedName("deliveredQuantity")
    @ColumnInfo(name = "deliveredQuantity")
    var deliveredQuantity: Int = 0,
    @SerializedName("reference")
    @ColumnInfo(name = "reference")
    var reference: String = "",
    @SerializedName("referenceDescription")
    @ColumnInfo(name = "referenceDescription")
    var referenceDescription: String = "",
    @SerializedName("weight")
    @ColumnInfo(name = "weight")
    var weight: Double = 0.0,
    @SerializedName("deliveryId")
    @ColumnInfo(name = "deliveryId")
    var deliveryId: Long = 0,
    @SerializedName("planificationId")
    @ColumnInfo(name = "planificationId")
    var planificationId: Long = 0,
    @SerializedName("uploaded")
    @ColumnInfo(name = "uploaded")
    var uploaded: Boolean = false,
    @SerializedName("delivered")
    @ColumnInfo(name = "delivered")
    var delivered: Boolean? = null,
    @SerializedName("index")
    @ColumnInfo(name = "index")
    var index: Int = 0,
    @SerializedName("scannedOrder")
    @ColumnInfo(name = "scannedOrder")
    var scannedOrder: Int? = null) : Serializable {

}