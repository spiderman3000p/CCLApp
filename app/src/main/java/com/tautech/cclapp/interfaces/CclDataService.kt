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
        @Header("Authorization") authorization: String
    ): Call<PlanificationsResponse>

    @GET
    @Headers("Content-Type: application/json")
    fun getPlanificationLines(
        @Url url: String,
        @Header("Authorization") authorization: String
    ): Call<PlanificationLinesResponse>

    @GET
    @Headers("Content-Type: application/json")
    fun getDeliveryDeliveryLines(
        @Url url: String,
        @Header("Authorization") authorization: String
    ): Call<DeliveryDeliveryLinesResponse>

    @GET
    @Headers("Content-Type: application/json")
    fun getPlanificationDeliveryLines(
        @Url url: String,
        @Header("Authorization") authorization: String
    ): Call<PlanificationDeliveryLinesResponse>

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
        @Header("Authorization") authorization: String): Call<Void>

    @POST
    @Headers("Content-Type: application/json")
    fun changeDeliveryState(
        @Url url: String,
        @Header("Authorization") authorization: String): Call<Void>

    @GET
    @Headers("Content-Type: application/json")
    fun getStateFormDefinitions(
        @Url url: String,
        @Header("Authorization") authorization: String
    ): Call<ArrayList<StateFormDefinition>>

    @GET
    @Headers("Content-Type: application/json")
    fun getCustomer(
        @Url url: String,
        @Header("Authorization") authorization: String
    ): Call<Customer>

    @POST
    @Headers("Content-Type: application/json")
    fun savePlanificationStateForm(
        @Url url: String,
        @Body stateForm: StateForm,
        @Header("customer-id") customerId: Long?,
        @Header("Authorization") authorization: String
        ): Call<Long>

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
        @Body deliveredItems: List<DeliveredItemToUpload>?,
        @Header("Authorization") authorization: String,
        @Url url: String = "delivery/addDeliveredItems"
    ): Call<Void>

    @GET
    @Headers("Content-Type: application/json")
    fun getDeliveredItems(
        @Query(value = "") deliveryId: Long,
        @Header("Authorization") authorization: String,
        @Url url: String = "delivery/deliveredItems"
    ): Call<Void>

    @GET
    @Headers("Content-Type: application/json")
    fun getPaymentMethods(
        @Header("Authorization") authorization: String,
        @Url url: String = "paymentMethods"): Call<PaymentMethodsResponse>

    @POST
    @Headers("Content-Type: application/json")
    fun saveDeliveryPaymentDetails(
        @Body paymentDetails: DeliveryPaymentDetail,
        @Header("Authorization") authorization: String,
        @Url url: String = "paymentDetails"
    ): Call<Void>
}