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
import com.tautech.cclapp.services.MyWorkerManagerService
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

class UploadFailedCertificationsWorker
    (val appContext: Context,
    val workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    private val TAG = "UPLOAD_CERTIFICATIONS_WORKER"
    var db: AppDatabase? = null
    private var retrofitClient: Retrofit? = null
    private var mAuthService: AuthorizationService? = null
    private var mStateManager: AuthStateManager? = null
    private var mConfiguration: Configuration? = null
    private var pendingCertifications: List<PendingToUploadCertification>? = null

    fun initAll(){
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
            //showAlert(getString(R.string.error), getString(R.string.unauthorized_user))
            Log.e(TAG, "No hay autorizacion para el usuario. Sesion de usuario ha finalizado")
        }
    }

    override fun doWork(): Result {
        initAll()
        pendingCertifications = db?.pendingToUploadCertificationDao()?.getAll()
        if (!pendingCertifications.isNullOrEmpty()) {
            val dataService: CclDataService? = CclClient.getInstance()?.create(
                CclDataService::class.java)
            if (dataService != null && mStateManager?.current?.accessToken != null) {
                for(pendingCertification in pendingCertifications!!) {
                    MyWorkerManagerService.enqueUploadSingleCertificationWork(appContext, pendingCertification)
                }
            } else {
                Log.e(TAG, "El cliente http o la autenticacion de usuario son invalidos. No se puede realizar el proceso")
            }
        }
        return Result.success()
    }
}