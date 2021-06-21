package com.tautech.cclapp.classes

import android.content.Context
import android.database.sqlite.SQLiteAccessPermException
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.tautech.cclapp.database.AppDatabase
import com.tautech.cclapp.interfaces.CclDataService
import com.tautech.cclapp.models.*
import com.tautech.cclapp.services.CclClient
import com.tautech.cclapp.services.MyWorkerManagerService
import java.io.IOException
import java.net.SocketTimeoutException

class UploadSingleLegalizationArrayWorker
    (val appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    private val TAG = "UPLOAD_SINGLE_LEGALIZATION_ARRAY_WORKER"
    private val MAX_REINTENT = 3
    private var failedRequestsCounter = 0
    var db: AppDatabase? = null
    private var mStateManager: AuthStateManager? = null

    override fun doWork(): Result {
        mStateManager = AuthStateManager.getInstance(appContext)
        try {
            db = AppDatabase.getDatabase(appContext)
        } catch(ex: SQLiteDatabaseLockedException) {
            Log.e(TAG, "Database error found", ex)
        } catch (ex: SQLiteAccessPermException) {
            Log.e(TAG, "Database error found", ex)
        } catch (ex: SQLiteCantOpenDatabaseException) {
            Log.e(TAG, "Database error found", ex)
        }
        if (!mStateManager!!.current.isAuthorized) {
            Log.e(TAG, "No hay autorizacion para el usuario. Sesion de usuario ha finalizado")
            return Result.failure()
        }
        Log.i(TAG, "Iniciando... inputData $inputData")
        val jsonStr = inputData.getString("paymentDetailsJSONArray")
        val planificationId = inputData.getLong("planificationId", 0)
        Log.i(TAG, "jsonStr $jsonStr")
        val pendingLegalizations: List<PendingToUploadLegalization> = Gson().fromJson(jsonStr, Array<PendingToUploadLegalization>::class.java).toList()
        val legalizationDetails: List<PlanificationPaymentDetail> = Gson().fromJson(jsonStr, Array<PlanificationPaymentDetail>::class.java).toList()
        Log.i(TAG, "legalizationDetails $legalizationDetails")
        val totalPayments = (legalizationDetails.fold(0.0, { sum, _payment ->
            sum + (_payment.amount ?: 0.0)
        }))
        val paymentLegalization = PaymentLegalization(
            planificationId = planificationId,
            amount = totalPayments,
            detail = legalizationDetails.map {
                it.hasPhoto = null
                it.paymentMethod = null
                it
            }
        )
        val dataService: CclDataService? = CclClient.getInstance()?.create(
            CclDataService::class.java)
        if (dataService != null && mStateManager?.current?.accessToken != null) {
            val url = "planification/${planificationId}/legalization"
            Log.i(TAG, "guardando legalizacion: $paymentLegalization")
                try {
                    val call = dataService.legalizePlanificationPayments(url, paymentLegalization,
                        "Bearer ${mStateManager?.current?.accessToken}")
                        .execute()
                    if (call.code() == 500 || call.code() == 400 || call.code() == 404 || call.code() == 403 || call.code() == 401) {
                        Log.e(TAG, "upload legalizations error response: ${call.errorBody()}")
                        return if (failedRequestsCounter < MAX_REINTENT) {
                            failedRequestsCounter++
                            Log.i(TAG, "Las legalizaciones no se pudieron guardar en la BD remota. Reintentando...")
                            Result.retry()
                        } else {
                            Log.i(TAG, "guardando legalizaciones en la tabla de requests fallidas $pendingLegalizations")
                            db?.pendingToUploadLegalizationDao()?.insertAll(pendingLegalizations)
                            Log.i(TAG, "Payments no pudieron guardar en la BD remota. Todos los intentos fallidos")
                            Result.failure()
                        }
                    } else if (call.code() == 200 || call.code() == 201 || call.code() == 202) {
                        db?.deliveryPaymentDetailDao()?.insertAll(legalizationDetails.toMutableList())
                        db?.pendingToUploadPaymentDao()?.deleteAllByCode(legalizationDetails.map {
                            it.code
                        })
                        call.body()?.let {savedPayments ->
                            savedPayments.forEach {savedPayment ->
                                pendingLegalizations.find {
                                    it.code == savedPayment.code
                                }?.let { legalizaion ->
                                    if (legalizaion.hasPhoto == true) {
                                        MyWorkerManagerService.enqueLegalizationPhotoUpload(appContext,
                                            savedPayment.code,
                                            savedPayment.id)
                                    }
                                }
                            }
                        }
                        return Result.success()
                    }
                } catch(toe: SocketTimeoutException) {
                    Log.e(TAG, "Network error when uploading legalization $paymentLegalization", toe)
                    return if (failedRequestsCounter < MAX_REINTENT) {
                        failedRequestsCounter++
                        Result.retry()
                    } else {
                        db?.pendingToUploadLegalizationDao()?.insertAll(pendingLegalizations)
                        Result.failure()
                    }
                } catch (ioEx: IOException) {
                    Log.e(TAG,
                        "Network error when uploading legalization $paymentLegalization",
                        ioEx)
                    return if (failedRequestsCounter < MAX_REINTENT) {
                        failedRequestsCounter++
                        Result.retry()
                    } else {
                        db?.pendingToUploadLegalizationDao()?.insertAll(pendingLegalizations)
                        Result.failure()
                    }
                }
            } else {
            Log.e(TAG, "El cliente http o la autenticacion de usuario son invalidos. No se puede realizar el proceso")
        }
        return Result.failure()
    }
}