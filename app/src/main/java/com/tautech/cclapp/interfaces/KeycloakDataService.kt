package com.tautech.cclapp.interfaces

import com.tautech.cclapp.models.KeycloakUser
import org.json.JSONObject
import retrofit2.Call
import retrofit2.http.*

interface KeycloakDataService {

    @GET
    @Headers("Content-Type: application/json")
    public fun getUserInfo(
        @Url url: String,
        @Header("Authorization") authorization: String
    ): Call<JSONObject>;

    @GET
    @Headers("Content-Type: application/json")
    public fun getUserProfile(
        @Url url: String,
        @Header("Authorization") authorization: String
    ): Call<KeycloakUser>;
}