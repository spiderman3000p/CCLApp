package com.tautech.cclapp.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable
import java.util.*

@Entity(primaryKeys = ["table", "user_id"])
data class CachedData(
    @ColumnInfo(name = "table")
    var table: String,
    @ColumnInfo(name = "user_id")
    var user_id: Long,
    @ColumnInfo(name = "timestamp")
    var timestamp: Long): Serializable {

}