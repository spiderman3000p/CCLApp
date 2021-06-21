package com.tautech.cclapp.classes

import android.content.Context
import android.database.sqlite.SQLiteAccessPermException
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.tautech.cclapp.database.AppDatabase
import com.tautech.cclapp.interfaces.CclDataService
import com.tautech.cclapp.models.*
import com.tautech.cclapp.services.CclClient
import java.io.IOException
import java.net.SocketTimeoutException

class UploadSinglePaymentWorker
    (val appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    private val TAG = "UPLOAD_SINGLE_PAYMENT_WORKER"
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
        if (inputData.hasKeyWithValueOfType("code", String::class.java) &&
            inputData.hasKeyWithValueOfType("amount", Double::class.java) &&
            inputData.hasKeyWithValueOfType("paymentMethodId", Long::class.java) &&
            inputData.hasKeyWithValueOfType("transactionNumber", String::class.java) &&
            inputData.hasKeyWithValueOfType("hasPhoto", Boolean::class.java)) {
            val pendingToUploadPayment = PendingToUploadPayment(code = inputData.getString("code")!!,
                amount = inputData.getDouble("amount", 0.0),
                paymentMethodId = inputData.getLong("paymentMethodId", 0),
                transactionNumber = inputData.getString("transactionNumber"),
                hasPhoto = inputData.getBoolean("hasPhoto", false)
            )
            val payment = PlanificationPaymentDetail(code = inputData.getString("code")!!,
                amount = inputData.getDouble("amount", 0.0),
                paymentMethodId = inputData.getLong("paymentMethodId", 0),
                transactionNumber = inputData.getString("transactionNumber"),
                hasPhoto = inputData.getBoolean("hasPhoto", false)
            )
            val deliveryId = inputData.getLong("deliveryId", 0)
            val dataService: CclDataService? = CclClient.getInstance()?.create(
                CclDataService::class.java)
            if (dataService != null && mStateManager?.current?.accessToken != null) {
                val url = "delivery/${deliveryId}/payment-detail"
                Log.i(TAG, "guardando payment $pendingToUploadPayment")
                    try {
                        val call = dataService.saveDeliveryPayment(url, payment,
                            "Bearer ${mStateManager?.current?.accessToken}")
                            .execute()
                        if (call.code() == 500 || call.code() == 400 || call.code() == 404 || call.code() == 403 || call.code() == 401) {
                            Log.e(TAG, "upload payment error response: ${call.errorBody()}")
                            return if (failedRequestsCounter < MAX_REINTENT) {
                                failedRequestsCounter++
                                Log.i(TAG, "Payment no pudo ser guardada en la BD remota. Reintentando...")
                                Result.retry()
                            } else {
                                Log.i(TAG, "guardando payment en la tabla de requests fallidas $payment")
                                db?.pendingToUploadPaymentDao()?.insert(pendingToUploadPayment)
                                Log.i(TAG, "Payment no pudo ser guardada en la BD remota. Todos los intentos fallidos")
                                Result.failure()
                            }
                        } else if (call.code() == 200 || call.code() == 201 || call.code() == 202) {
                            db?.deliveryPaymentDetailDao()?.insert(payment)
                            db?.pendingToUploadPaymentDao()?.delete(pendingToUploadPayment)
                            return Result.success()
                        }
                    } catch(toe: SocketTimeoutException) {
                        Log.e(TAG, "Network error when uploading payment $payment", toe)
                        return if (failedRequestsCounter < MAX_REINTENT) {
                            failedRequestsCounter++
                            Result.retry()
                        } else {
                            db?.pendingToUploadPaymentDao()?.insert(pendingToUploadPayment)
                            Result.failure()
                        }
                    } catch (ioEx: IOException) {
                        Log.e(TAG,
                            "Network error when uploading payment $payment",
                            ioEx)
                        return if (failedRequestsCounter < MAX_REINTENT) {
                            failedRequestsCounter++
                            Result.retry()
                        } else {
                            db?.pendingToUploadPaymentDao()?.insert(pendingToUploadPayment)
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