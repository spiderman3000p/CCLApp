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
    fun getBanks(
        @Url url: String,
        @Header("Authorization") authorization: String
    ): Call<BanksResponse>

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

    @GET
    @Headers("Content-Type: application/json")
    fun getPlanificationDetails3(
        @Url url: String,
        @Header("Authorization") authorization: String
    ): Call<PlanificationDetails>

    @GET
    @Headers("Content-Type: application/json")
    fun getPlanificationPaymentDetails(
        @Url url: String,
        @Header("Authorization") authorization: String
    ): Call<PlanificationPaymentDetailsResponse>

    @POST
    @Headers("Content-Type: application/json")
    fun legalizePlanificationPayments(
        @Url url: String,
        @Body payments: PaymentLegalization,
        @Header("Authorization") authorization: String): Call<List<SavePaymentResponse>>

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
    fun uploadFile(
        @Url url: String,
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
    fun getPaymentMethods(
        @Header("Authorization") authorization: String,
        @Url url: String = "paymentMethods"): Call<PaymentMethodsResponse>

    @POST
    @Headers("Content-Type: application/json")
    fun saveDeliveryPaymentDetails(
        @Url url: String,
        @Body paymentDetails: List<PendingToUploadPayment>,
        @Header("Authorization") authorization: String
    ): Call<ArrayList<SavePaymentResponse>>

    @POST
    @Headers("Content-Type: application/json")
    fun saveDeliveryPayment(
        @Url url: String,
        @Body paymentDetail: PlanificationPaymentDetail,
        @Header("Authorization") authorization: String
    ): Call<SavePaymentResponse>
}