package com.tautech.cclapp.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import org.json.JSONArray
import java.io.Serializable

@Entity
data class StateForm(
    @PrimaryKey
    @SerializedName("id")
    @ColumnInfo(name = "id")
    var id: Long? = 0,
    @SerializedName("state")
    @ColumnInfo(name = "state")
    var state: String = "",
    @SerializedName("data")
    @ColumnInfo(name = "data")
    var data: ArrayList<Item>? = null
    ) : Serializable {

}