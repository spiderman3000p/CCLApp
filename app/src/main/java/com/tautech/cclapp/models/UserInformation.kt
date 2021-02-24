package com.tautech.cclapp.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Entity
data class UserInformation(
    @PrimaryKey
    @SerializedName("email")
    var email: String = "",
    @SerializedName("username")
    var username: String = ""): Serializable {

}