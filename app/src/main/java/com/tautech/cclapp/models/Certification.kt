package com.tautech.cclapp.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable
import java.util.*

@Entity
data class Certification(
    @PrimaryKey(autoGenerate = true)
    var id: Long? = 0,
    @ColumnInfo(name = "quantity")
    var quantity: Int = 1,
    @ColumnInfo(name = "index")
    var index: Int = 0,
    @ColumnInfo(name = "deliveryLineId")
    var deliveryLineId: Long = 0,
    @ColumnInfo(name = "planificationId")
    var planificationId: Long = 0,
    @ColumnInfo(name = "deliveryId")
    var deliveryId: Long = 0): Serializable {

}