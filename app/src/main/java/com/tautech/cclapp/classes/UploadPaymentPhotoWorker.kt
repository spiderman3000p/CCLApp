package com.tautech.cclapp.classes

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteAccessPermException
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.hasKeyWithValueOfType
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tautech.cclapp.database.AppDatabase
import com.tautech.cclapp.interfaces.CclDataService
import com.tautech.cclapp.models.Payment
import com.tautech.cclapp.services.CclClient
import com.tautech.cclapp.services.MyWorkerManagerService
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Retrofit
import java.io.IOException
import java.net.SocketTimeoutException


class UploadPaymentPhotoWorker
    (
    val appContext: Context,
    workerParams: WorkerParameters,
) : Worker(appContext, workerParams) {
    private val TAG = "UPLOAD_PAYMENT_PHOTO_WORKER"
    private val MAX_REINTENT = 3
    private var failedRequestsCounter = 0
    var db: AppDatabase? = null
    private var mStateManager: AuthStateManager? = null

    override fun doWork(): Result {
        mStateManager = AuthStateManager.getInstance(appContext)
        try {
            db = AppDatabase.getDatabase(appContext)
        } catch (ex: SQLiteDatabaseLockedException) {
            return when(failedRequestsCounter < MAX_REINTENT){
                true -> {
                    failedRequestsCounter++
                    Log.e(TAG, "Database error found", ex)
                    Result.retry()
                }
                else -> Result.failure()
            }
        } catch (ex: SQLiteAccessPermException) {
            return when(failedRequestsCounter < MAX_REINTENT){
                true -> {
                    failedRequestsCounter++
                    Log.e(TAG, "Database error found", ex)
                    Result.retry()
                }
                else -> Result.failure()
            }
        } catch (ex: SQLiteCantOpenDatabaseException) {
            return when(failedRequestsCounter < MAX_REINTENT){
                true -> {
                    failedRequestsCounter++
                    Log.e(TAG, "Database error found", ex)
                    Result.retry()
                }
                else -> Result.failure()
            }
        }
        if (!mStateManager!!.current.isAuthorized) {
            Log.e(TAG, "No hay autorizacion para el usuario. Sesion de usuario ha finalizado")
            mStateManager?.refreshAccessToken()
            return when(failedRequestsCounter < MAX_REINTENT){
                true -> {
                    failedRequestsCounter++
                    Result.retry()
                }
                else -> Result.failure()
            }
        }
        if (inputData.hasKeyWithValueOfType<Long>("savedPaymentId") &&
            inputData.hasKeyWithValueOfType<String>("code") &&
            inputData.hasKeyWithValueOfType<Long>("customerId")) {
            val savedPaymentId = inputData.getLong("savedPaymentId", 0)
            val customerId = inputData.getLong("customerId", 0)
            val code = inputData.getString("code")
            val file = MyWorkerManagerService.filesToUpload[code]
            if (file != null) {
                try {
                    val fileUri: Uri = FileProvider.getUriForFile(
                        appContext,
                        "com.tautech.cclapp.fileprovider",
                        file
                    )
                    val mediaTypeStr = appContext.contentResolver?.getType(fileUri)
                    if (mediaTypeStr != null) {
                        val requestFile = RequestBody.create(MediaType.parse("multipart/form-data"),
                            file)
                        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                        val dataService: CclDataService? = CclClient.getInstance()?.create(
                            CclDataService::class.java)
                        if (dataService != null && mStateManager?.current?.accessToken != null) {
                            Log.i(TAG, "uploading $code payment photo...")
                            val urlSaveForm = "delivery/payment-detail/upload-file/${savedPaymentId}?propertyName=photo"
                            try {
                                val callSaveForm = dataService.uploadFile(
                                    urlSaveForm,
                                    body,
                                    customerId,
                                    "Bearer ${mStateManager?.current?.accessToken}")
                                    .execute()
                                Log.i(TAG,
                                    "save item photo $code response code ${callSaveForm.code()}")
                                if(callSaveForm.code() == 200){
                                    return Result.success()
                                }
                            } catch (toe: SocketTimeoutException) {
                                Log.e(TAG, "Network error when saving payment $code photo", toe)
                                return when(failedRequestsCounter < MAX_REINTENT){
                                    true -> {
                                        failedRequestsCounter++
                                        Result.retry()
                                    }
                                    else -> {
                                        Result.failure()
                                    }
                                }
                            } catch (ioEx: IOException) {
                                Log.e(TAG,
                                    "Network error when saving payment $code photo",
                                    ioEx)
                                return when(failedRequestsCounter < MAX_REINTENT){
                                    true -> {
                                        failedRequestsCounter++
                                        Result.retry()
                                    }
                                    else -> {
                                        Result.failure()
                                    }
                                }
                            }
                        } else {
                            Log.e(TAG,
                                "El cliente http o la autenticacion de usuario son invalidos. No se puede realizar el proceso")
                            return when(failedRequestsCounter < MAX_REINTENT){
                                true -> {
                                    failedRequestsCounter++
                                    mStateManager?.refreshAccessToken()
                                    Result.retry()
                                }
                                else -> {
                                    Result.failure()
                                }
                            }
                        }
                    } else {
                        Log.e(TAG, "mimetype de foto del pago $code es invalido")
                    }
                } catch(ex: Exception){
                    FirebaseCrashlytics.getInstance().recordException(ex)
                    Log.e(TAG, "ocurrio una excepcion al parsear uri a archivo", ex)
                }
            } else {
                Log.e(TAG, "el archivo del pago $code es invalido")
            }
        } else {
            Log.e(TAG, "no se recibieron los parametros esperados: $inputData")
        }
        return Result.failure()
    }

    fun getRealPathFromURI(context: Context, contentUri: Uri?): String? {
        var cursor: Cursor? = null
        return try {
            val proj = arrayOf(MediaStore.Images.Media.DATA)
            cursor = context.contentResolver.query(contentUri!!, proj, null, null, null)
            val columnIndex: Int = cursor?.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)!!
            cursor.moveToFirst()
            cursor.getString(columnIndex)
        } catch(ex: Exception) {
            FirebaseCrashlytics.getInstance().recordException(ex)
            Log.e(TAG, "Excepcion encontrada al obtener path de uri", ex)
            null
        } finally {
            cursor?.close()
        }
    }
}