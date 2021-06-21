package com.tautech.cclapp.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable
@Entity
data class Bank(
    @PrimaryKey
    var id: Long,
    @ColumnInfo(name = "code")
    var code: String?,
    @ColumnInfo(name = "name")
    var name: String?): Serializable