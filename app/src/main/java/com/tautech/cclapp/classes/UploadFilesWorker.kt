package com.tautech.cclapp.classes

import android.content.Context
import android.content.DialogInterface
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.tautech.cclapp.R
import com.tautech.cclapp.interfaces.CclDataService
import com.tautech.cclapp.models.StateForm
import com.tautech.cclapp.services.CclClient
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import okhttp3.MultipartBody
import org.jetbrains.anko.doAsync
import retrofit2.Retrofit
import java.io.IOException
import java.net.SocketTimeoutException

class UploadFilesWorker
    (
    val appContext: Context,
    val workerParams: WorkerParameters,
    val deliveryId: Long?,
    val customerId: Long?,
    formDataWithFiles: StateForm?
) : Worker(appContext, workerParams) {
    private val TAG = "UPLOAD_FILES_WORKER"
    private var retrofitClient: Retrofit? = null
    private var mAuthService: AuthorizationService? = null
    private var mStateManager: AuthStateManager? = null
    private var mConfiguration: Configuration? = null
    private var formDataWithFiles: StateForm? = null
    init {
        initAll()
    }

    fun initAll(){
        retrofitClient = CclClient.getInstance()
        mStateManager = AuthStateManager.getInstance(this.appContext)
        mConfiguration = Configuration.getInstance(appContext)
        val config = Configuration.getInstance(appContext)
        if (config.hasConfigurationChanged()) {
            Toast.makeText(
                appContext,
                "Configuration change detected",
                Toast.LENGTH_SHORT)
                .show()
            mStateManager?.signOut(appContext)
            return
        }
        mAuthService = AuthorizationService(
            appContext,
            AppAuthConfiguration.Builder()
                .setConnectionBuilder(config.connectionBuilder)
                .build())
        if (!mStateManager!!.current.isAuthorized) {
            //showAlert(getString(R.string.error), getString(R.string.unauthorized_user))
            Log.i(TAG, "No hay autorizacion para el usuario")
            if (mStateManager?.signOut(appContext) == true){

            }
            return
        }
    }

    override fun doWork(): Result {
        saveDeliveryStateFormWithFiles()
        return Result.success()
    }

    private fun fetchData(callback: ((String?, String?, AuthorizationException?) -> Unit)) {
        Log.i(TAG, "Fetching user planifications...")
        try {
            mStateManager?.current?.performActionWithFreshTokens(mAuthService!!,
                callback)
        }catch (ex: AuthorizationException) {
            Log.e(TAG, "error fetching data", ex)
        }
    }

    fun saveDeliveryStateFormWithFiles() {
        if (formDataWithFiles != null) {
            fetchData(this::saveDeliveryStateFormWithFiles)
        }
    }

    private fun saveDeliveryStateFormWithFiles(accessToken: String?, idToken: String?, ex: AuthorizationException?) {
        if (ex != null) {
            Log.e(TAG, "Token refresh failed when finalizing planification load", ex)
            AuthStateManager.driverInfo = null
            if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {
                //showAlert("Error", "Sesion Expirada", this::signOut)
            }
            return
        }
        val dataService: CclDataService? = CclClient.getInstance()?.create(
            CclDataService::class.java)
        if (dataService != null && accessToken != null) {
            formDataWithFiles?.data?.forEach { item ->
                if (item.value != null && item.value is MultipartBody.Part) {
                    val urlSaveForm =
                        "delivery/state-history/upload-file/${deliveryId}?propertyName=${item.name}"
                    /*val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "com.tautech.cclapp.fileprovider",
                        item.value as File
                    )*/
                    doAsync {
                        Toast.makeText(applicationContext, "guardando ${item.name}: ", Toast.LENGTH_SHORT).show()
                        try {
                            val callSaveForm = dataService.savePlanificationStateFormFile(urlSaveForm,
                                item.value as MultipartBody.Part,
                                customerId,
                                "Bearer $accessToken")
                                .execute()
                            //hideLoader()
                            val responseSaveForm = callSaveForm.body()
                            Log.i(TAG, "save file ${item.name} response $responseSaveForm")
                            if (responseSaveForm != null) {
                                //showSnackbar(getString(R.string.form_saved_successfully))
                            }
                        } catch(toe: SocketTimeoutException) {
                            //hideLoader()
                            Log.e(TAG, "Network error when saving ${item.name} file", toe)
                            //showAlert(getString(R.string.network_error_title), getString(R.string.network_error))
                        } catch (ioEx: IOException) {
                            /*hideLoader()*/
                            Log.e(TAG,
                                "Network error when saving ${item.name} file",
                                ioEx)
                            /*showAlert(getString(R.string.network_error_title), getString(R.string.network_error))*/
                        }
                    }
                } else {
                    //showSnackbar(getString(R.string.invalid_file_for, item.name))
                }
            }
        }
    }

    fun showAlert(title: String, message: String, positiveCallback: (() -> Unit)? = null, negativeCallback: (() -> Unit)? = null) {
        val builder = AlertDialog.Builder(applicationContext)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("Aceptar", DialogInterface.OnClickListener { dialog, id ->
            if (positiveCallback != null) {
                positiveCallback()
            }
            dialog.dismiss()
        })
        builder.setNegativeButton("Cancelar", DialogInterface.OnClickListener { dialog, id ->
            if (negativeCallback != null) {
                negativeCallback()
            }
            dialog.dismiss()
        })
        val dialog: AlertDialog = builder.create()
        dialog.show()
    }
}