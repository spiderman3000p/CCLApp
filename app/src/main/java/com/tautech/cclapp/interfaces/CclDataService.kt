package com.tautech.cclapp.interfaces

import com.tautech.cclapp.models.*
import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.*

interface CclDataService {

    @GET
    @Headers("Content-Type: application/json")
    fun getDriverInfo(
        @Url url: String,
        @Header("Authorization") authorization: String
    ): Call<Driver>;

    @GET
    @Headers("Content-Type: application/json")
    fun getPlanifications(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Header("driver-id") driverId: Long,
        /*@Header("address-id") addressId: Long*/
    ): Call<PlanificationsResponse>

    @POST
    @Headers("Content-Type: application/json")
    fun getPlanificationLines(
    @Url url: String,
    @Header("Authorization") authorization: String,
    @Body IdsArray: ArrayList<Long>
    ): Call<ArrayList<PlanificationWithLine>>

    @POST
    @Headers("Content-Type: application/json")
    fun saveCertifiedDeliveryLine(
        @Body certifiedDeliveryLine: CertificationToUpload,
        @Header("Authorization") authorization: String,
        @Url url: String = "planificationCertifications"
    ): Call<Void>

    @GET
    @Headers("Content-Type: application/json")
    fun getPlanificationsCertifiedLines(
        @Url url: String,
        @Header("Authorization") authorization: String
    ): Call<PlanificationCertificationsResponse>

    @POST
    @Headers("Content-Type: application/json")
    fun changePlanificationState(
        @Url url: String,
        @Header("Authorization") authorization: String): Call<Any?>

    @GET
    @Headers("Content-Type: application/json")
    fun getStateFormDefinitions(
        @Url url: String,
        @Header("Authorization") authorization: String
    ): Call<ArrayList<StateFormDefinition>>

    @POST
    @Headers("Content-Type: application/json")
    fun savePlanificationStateForm(
        @Url url: String,
        @Body stateForm: StateForm,
        @Header("customer-id") customerId: Long?,
        @Header("Authorization") authorization: String
        ): Call<Int>

    @POST
    @Multipart
    fun savePlanificationStateFormFile(
        @Url url: String,
        /*@Part("name") filename: RequestBody,
        @Part("mimeType") mimeType: RequestBody,*/
        @Part filePart: MultipartBody.Part,
        @Header("customer-id") customerId: Long?,
        @Header("Authorization") authorization: String
    ): Call<Void>

    @POST
    @Headers("Content-Type: application/json")
    fun saveDeliveredItems(
        @Url url: String,
        @Body deliveredItems: List<Any>,
        @Header("customer-id") customerId: Long?,
        @Header("Authorization") authorization: String
    ): Call<Int>
}