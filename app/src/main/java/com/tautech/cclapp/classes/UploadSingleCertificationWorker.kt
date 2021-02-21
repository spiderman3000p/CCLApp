package com.tautech.cclapp.classes

import android.content.Context
import android.content.DialogInterface
import android.database.sqlite.SQLiteAccessPermException
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.work.*
import com.tautech.cclapp.database.AppDatabase
import com.tautech.cclapp.interfaces.CclDataService
import com.tautech.cclapp.models.Certification
import com.tautech.cclapp.models.CertificationToUpload
import com.tautech.cclapp.models.PendingToUploadCertification
import com.tautech.cclapp.models.StateForm
import com.tautech.cclapp.services.CclClient
import kotlinx.coroutines.runBlocking
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import okhttp3.MultipartBody
import org.jetbrains.anko.doAsync
import retrofit2.Retrofit
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class UploadSingleCertificationWorker
    (val appContext: Context,
    val workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    private val TAG = "UPLOAD_SINGLE_CERTIFICATION_WORKER"
    private val MAX_REINTENT = 3
    var db: AppDatabase? = null
    private var retrofitClient: Retrofit? = null
    private var mAuthService: AuthorizationService? = null
    private var mStateManager: AuthStateManager? = null
    private var mConfiguration: Configuration? = null
    private var failedRequestsCounter = 0

    override fun doWork(): Result {
        retrofitClient = CclClient.getInstance()
        mStateManager = AuthStateManager.getInstance(appContext)
        mConfiguration = Configuration.getInstance(appContext)
        try {
            db = AppDatabase.getDatabase(appContext)
        } catch(ex: SQLiteDatabaseLockedException) {
            Log.e(TAG, "Database error found", ex)
        } catch (ex: SQLiteAccessPermException) {
            Log.e(TAG, "Database error found", ex)
        } catch (ex: SQLiteCantOpenDatabaseException) {
            Log.e(TAG, "Database error found", ex)
        }
        val config = Configuration.getInstance(appContext)
        mAuthService = AuthorizationService(
            appContext,
            AppAuthConfiguration.Builder()
                .setConnectionBuilder(config.connectionBuilder)
                .build())
        if (!mStateManager!!.current.isAuthorized) {
            Log.e(TAG, "No hay autorizacion para el usuario. Sesion de usuario ha finalizado")
            return Result.failure()
        }
        if (!inputData.hasKeyWithValueOfType("planification", String::class.java)) {
            val pendingToUploadCertification = PendingToUploadCertification(quantity = inputData.getInt("quantity", 0),
                deliveryId = inputData.getLong("deliveryId", 0),
                planificationId = inputData.getLong("planificationId", 0),
                index = inputData.getInt("index", 0),
                deliveryLineId = inputData.getLong("deliveryLineId", 0)
            )
            val certificationToUpload = CertificationToUpload()
            certificationToUpload.quantity = pendingToUploadCertification.quantity
            certificationToUpload.index = pendingToUploadCertification.index
            certificationToUpload.delivery = "${CclClient.BASE_URL}deliveries/${pendingToUploadCertification.deliveryId}"
            certificationToUpload.planification = "${CclClient.BASE_URL}planifications/${pendingToUploadCertification.planificationId}"
            certificationToUpload.deliveryLine = "${CclClient.BASE_URL}deliveryLines/${pendingToUploadCertification.deliveryLineId}"
            val dataService: CclDataService? = CclClient.getInstance()?.create(
                CclDataService::class.java)
            if (dataService != null && mStateManager?.current?.accessToken != null) {
                Log.i(TAG, "guardando certificacion $certificationToUpload")
                    try {
                        val call = dataService.saveCertifiedDeliveryLine(certificationToUpload,
                            "Bearer ${mStateManager?.current?.accessToken}")
                            .execute()
                        if (call.code() == 500 || call.code() == 400 || call.code() == 404 || call.code() == 403 || call.code() == 401) {
                            Log.e(TAG, "upload certification error response: ${call.errorBody()}")
                            return if (failedRequestsCounter < MAX_REINTENT) {
                                failedRequestsCounter++
                                Result.retry()
                            } else {
                                Log.i(TAG, "guardando certificacion en la tabla de requests fallidas $pendingToUploadCertification")
                                db?.pendingToUploadCertificationDao()?.insert(pendingToUploadCertification)
                                Log.i(TAG, "Certificacion guardada en la BD remota con exito")
                                Result.failure()
                            }
                        } else if (call.code() == 200 || call.code() == 201 || call.code() == 202) {
                            val certification = Certification()
                            certification.quantity = pendingToUploadCertification.quantity
                            certification.index = pendingToUploadCertification.index
                            certification.deliveryId = pendingToUploadCertification.deliveryId
                            certification.planificationId = pendingToUploadCertification.planificationId
                            certification.deliveryLineId = pendingToUploadCertification.deliveryLineId
                            db?.certificationDao()?.insert(certification)
                            db?.pendingToUploadCertificationDao()?.delete(pendingToUploadCertification)
                            return Result.success()
                        }
                    } catch(toe: SocketTimeoutException) {
                        Log.e(TAG, "Network error when uploading certification $certificationToUpload", toe)
                        return if (failedRequestsCounter < MAX_REINTENT) {
                            failedRequestsCounter++
                            Result.retry()
                        } else {
                            db?.pendingToUploadCertificationDao()?.insert(pendingToUploadCertification)
                            Result.failure()
                        }
                    } catch (ioEx: IOException) {
                        Log.e(TAG,
                            "Network error when uploading certification $certificationToUpload",
                            ioEx)
                        return if (failedRequestsCounter < MAX_REINTENT) {
                            failedRequestsCounter++
                            Result.retry()
                        } else {
                            db?.pendingToUploadCertificationDao()?.insert(pendingToUploadCertification)
                            Result.failure()
                        }
                    }
                } else {
                Log.e(TAG, "El cliente http o la autenticacion de usuario son invalidos. No se puede realizar el proceso")
            }
        }
        return Result.failure()
    }
}