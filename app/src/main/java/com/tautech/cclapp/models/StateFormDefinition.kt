package com.tautech.cclapp.models

import androidx.room.*
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Entity (indices = [Index(value = ["deliveryState", "customerId"], name = "state_customer", unique = true)])
data class StateFormDefinition(
    @PrimaryKey
    @SerializedName("id")
    @ColumnInfo(name = "id")
    var id: Long? = 0,
    @SerializedName("deliveryState")
    @ColumnInfo(name = "deliveryState")
    var deliveryState: String = "",
    @SerializedName("customerId")
    @ColumnInfo(name = "customerId")
    var customerId: Long = 0,
    @SerializedName("description")
    @ColumnInfo(name = "description")
    var description: String = "",
    @SerializedName("formFieldList")
    @Ignore
    var formFieldList: List<StateFormField>? = listOf(),
    ) : Serializable {

}