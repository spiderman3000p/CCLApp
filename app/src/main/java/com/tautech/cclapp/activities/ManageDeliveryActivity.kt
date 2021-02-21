package com.tautech.cclapp.activities

import android.content.DialogInterface
import android.content.Intent
import android.database.sqlite.*
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import androidx.viewpager.widget.ViewPager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.tautech.cclapp.R
import com.tautech.cclapp.classes.AuthStateManager
import com.tautech.cclapp.classes.Configuration
import com.tautech.cclapp.database.AppDatabase
import com.tautech.cclapp.interfaces.CclDataService
import com.tautech.cclapp.services.CclClient
import com.tautech.cclapp.ui.main.SectionsPagerAdapter
import com.tautech.cclapp.activities.ui_delivery_detail.delivery_form.DeliveryFormFragment
import com.tautech.cclapp.models.*
import kotlinx.android.synthetic.main.activity_manage_delivery.*
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import okhttp3.MultipartBody
import org.jetbrains.anko.contentView
import org.jetbrains.anko.doAsync
import retrofit2.Retrofit
import java.io.IOException
import java.net.SocketTimeoutException

class ManageDeliveryActivity : AppCompatActivity() {
    private var filesSaved: Boolean = false
    private var savedItems: Boolean = false
    private var deliveredItemList: List<Any>? = null
    private var savedFormId: Int? = null
    val TAG = "MANAGE_DELIVERY_ACTIVITY"
    var planification: Planification? = null
    var delivery: PlanificationLine? = null
    var formDataWithoutFiles: StateForm? = null
    var formDataWithFiles: StateForm? = null
    private var retrofitClient: Retrofit? = null
    private var mAuthService: AuthorizationService? = null
    private var mStateManager: AuthStateManager? = null
    private var mConfiguration: Configuration? = null
    var db: AppDatabase? = null
    private val viewModel: ManageDeliveryActivityViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_delivery)
        val sectionsPagerAdapter = SectionsPagerAdapter(this, supportFragmentManager)
        val viewPager: ViewPager = findViewById(R.id.view_pager2)
        viewPager.adapter = sectionsPagerAdapter
        val tabs: TabLayout = findViewById(R.id.tabs2)
        //viewPager.setOffscreenPageLimit(2)
        tabs.setupWithViewPager(viewPager)
        doneBtn.isEnabled = false
        doneBtn.setOnClickListener { view ->
            val fragment: DeliveryFormFragment? = ((view_pager2 as ViewPager).adapter as SectionsPagerAdapter).formFragment
            //supportFragmentManager.findFragmentByTag("DeliveryForm") as DeliveryFormFragment?
            if (fragment != null && fragment.isValidForm()) {
                askForChangeState()
            }
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        retrofitClient = CclClient.getInstance()
        mStateManager = AuthStateManager.getInstance(this)
        mConfiguration = Configuration.getInstance(this)
        val config = Configuration.getInstance(this)
        try {
            db = AppDatabase.getDatabase(this)
        } catch(ex: SQLiteDatabaseLockedException) {
            Log.e(TAG, "Database error found", ex)
        } catch (ex: SQLiteAccessPermException) {
            Log.e(TAG, "Database error found", ex)
        } catch (ex: SQLiteCantOpenDatabaseException) {
            Log.e(TAG, "Database error found", ex)
        }
        if (config.hasConfigurationChanged()) {
            Toast.makeText(
                this,
                "Configuration change detected",
                Toast.LENGTH_SHORT)
                .show()
            signOut()
            return
        }
        mAuthService = AuthorizationService(
            this,
            AppAuthConfiguration.Builder()
                .setConnectionBuilder(config.connectionBuilder)
                .build())
        if (!mStateManager!!.current.isAuthorized) {
            showAlert(getString(R.string.error), getString(R.string.unauthorized_user))
            Log.i(TAG, "No hay autorizacion para el usuario")
            signOut()
            return
        }
        viewModel.state.observe(this, Observer{newState ->
            Log.i(TAG, "nuevo estado observado $newState")
            doAsync {
                Log.i(TAG, "buscando definiciones en la BD local para el estado $newState...")
                val foundStateFormDefinition = db?.stateFormDefinitionDao()?.getAllByStateAndCustomer(newState, planification?.customerId?.toInt()!!)
                if (foundStateFormDefinition != null) {
                    Log.i(TAG, "found state form definition: $foundStateFormDefinition")
                    foundStateFormDefinition.formFieldList = db?.stateFormDefinitionDao()?.getFields(foundStateFormDefinition.id!!.toInt())
                    Log.i(TAG, "found fieldList: ${foundStateFormDefinition.formFieldList}")
                } else {
                    Log.e(TAG, "form definitions not found for state $newState and customer ${planification?.customerId?.toInt()}")
                }
                viewModel.stateFormDefinition.postValue(foundStateFormDefinition)
                checkDoneBtnStatus()
            }
        })
        val extras = intent.extras
        if (extras != null) {
            // TODO obtener planificacion id de shared preferences y luego la planificacion de la BD
            if (extras.containsKey("planification")) {
                planification = extras.getSerializable("planification") as Planification
                viewModel.planification.postValue(planification)
            } else {
                showAlert(getString(R.string.error), getString(R.string.no_planification_received))
                Log.e(TAG, "no se recibio ninguna planificacion")
                finish()
            }
            if (extras.containsKey("delivery")) {
                delivery = extras.getSerializable("delivery") as PlanificationLine
                viewModel.delivery.postValue(delivery)
            } else {
                showAlert(getString(R.string.error), getString(R.string.no_delivery_received))
                Log.e(TAG, "no se recibio ninguna delivery")
                finish()
            }
            if (extras.containsKey("state")) {
                val newState = extras.getString("state") ?: ""
                Log.i(TAG, "estado recibido de actividad: $newState")
                if(newState.isEmpty()){
                    showAlert(getString(R.string.error), getString(R.string.no_state_received))
                    Log.e(TAG, "no se recibio ningun estado")
                    finish()
                } else {
                    viewModel.state.postValue(newState)
                }
            } else {
                Log.e(TAG, "no se recibio ningun estado")
                showAlert(getString(R.string.error), getString(R.string.no_state_received))
                finish()
            }
        } else {
            Log.i(TAG, "no se recibieron datos")
            showAlert(getString(R.string.error), getString(R.string.no_data_received))
            finish()
        }
        /*if (savedInstanceState != null) {
            if (savedInstanceState.containsKey("delivery")) {
                delivery = savedInstanceState.getSerializable("delivery") as PlanificationLine
                viewModel.delivery.postValue(delivery)
            }
            if (savedInstanceState.containsKey("planification")) {
                planification = savedInstanceState.getSerializable("planification") as Planification
                viewModel.planification.postValue(planification)
            }
            if (savedInstanceState.containsKey("stateFormDefinitions")) {
                val stateFormDefinitions = savedInstanceState.getSerializable("stateFormDefinitions") as MutableList<StateFormDefinition>
                viewModel.stateFormDefinitions.postValue(stateFormDefinitions)
            }
        }*/
    }

    fun checkDoneBtnStatus(){
        runOnUiThread {
            doneBtn.isEnabled = listOf("UnDelivered", "Delivered").contains(viewModel.state.value) && viewModel.stateFormDefinition.value != null
        }
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume()...")
        showLoader()
        doAsync {
            if (planification == null) {
                loadPlanification(planification?.id)
            }
            if (delivery == null) {
                loadDelivery(delivery?.id)
            }
            hideLoader()
        }
    }

    fun loadPlanification(planificationId: Long?){
        val _planification = db?.planificationDao()?.getById(planificationId?.toInt()!!)
        viewModel.planification.postValue(_planification)
        Log.i(TAG, "planification loaded from local DB: $_planification")
    }

    fun loadDelivery(deliveryId: Long?){
        Log.i(TAG, "loadDelivery from id: $deliveryId")
        val _delivery = db?.deliveryDao()?.getById(deliveryId)
        if (_delivery != null) {
            Log.i(TAG, "loadDelivery delivery loaded from bd local: $_delivery")
            val deliveryLines = db?.deliveryDao()?.getGroupedLines(deliveryId!!) ?: listOf()
            if (!deliveryLines.isNullOrEmpty()) {
                Log.i(TAG, "deliveryLines cargadas de la bd local: $deliveryLines")
                when (viewModel.state.value) {
                    "Delivered" -> {
                        deliveryLines.forEach { deliveryLine ->
                            deliveryLine.deliveredQuantity = deliveryLine.quantity
                        }
                    }
                    "UnDelivered" -> {
                        deliveryLines.forEach { deliveryLine ->
                            deliveryLine.deliveredQuantity = 0
                        }
                    }
                }
                _delivery.detail.addAll(deliveryLines)
            }
            viewModel.delivery.postValue(_delivery)
        }
        checkDoneBtnStatus()
        Log.i(TAG, "delivery loaded from local DB: $_delivery")
    }

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        // user info is retained to survive activity restarts, such as when rotating the
        // device or switching apps. This isn't essential, but it helps provide a less
        // jarring UX when these events occur - data does not just disappear from the view.
        if (planification != null) {
            state.putSerializable("planification", planification)
        }
        if (delivery != null) {
            state.putSerializable("delivery", delivery)
        }
        if (viewModel.stateFormDefinition.value != null) {
            // TODO: arreglar esto
            //state.putSerializable("stateFormDefinitions", viewModel.stateFormDefinitions.value)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    fun askForChangeState() {
        showAlert(getString(R.string.important), getString(R.string.manage_delivery_prompt), this::changeDeliveryState)
    }

    fun showAlert(title: String, message: String, positiveCallback: (() -> Unit)? = null, negativeCallback: (() -> Unit)? = null) {
        runOnUiThread {
            val builder = AlertDialog.Builder(this)
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

    fun changeDeliveryState() {
        val fragment: DeliveryFormFragment? = ((view_pager2 as ViewPager).adapter as SectionsPagerAdapter).formFragment
            //supportFragmentManager.findFragmentByTag("DeliveryForm") as DeliveryFormFragment?
        if (fragment != null) {
            formDataWithoutFiles = fragment.generateFormDataWithoutFiles()
            formDataWithFiles = fragment.generateFormDataWithFiles()
            deliveredItemList = generateDeliveredItemList()
            Log.i(TAG, "form data without files generated: $formDataWithoutFiles")
            Log.i(TAG, "form data with files generated: $formDataWithFiles")
            Log.i(TAG, "item list generated: $deliveredItemList")
            saveDeliveryStateFormWithoutFiles()
            /*val uploadWorkRequest: WorkRequest =
                OneTimeWorkRequestBuilder<UploadFilesWorker>()
                    .build()
            WorkManager
                .getInstance(applicationContext)
                .enqueue(uploadWorkRequest)*/
        } else {
            Toast.makeText(this, getString(R.string.no_state_form_found), Toast.LENGTH_SHORT).show()
        }
    }

    fun generateDeliveredItemList(): List<Any> {
        return viewModel.delivery.value?.detail?.map {deliveryLine ->
            object {
                val quantity = deliveryLine.deliveredQuantity
                val price = deliveryLine.price
                val deliveryLine = "${CclClient.BASE_URL}deliveryLines/${deliveryLine.id}"
            }
        } ?: listOf()
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

    fun saveDeliveryStateFormWithoutFiles() {
        if (formDataWithoutFiles != null) {
            fetchData(this::saveDeliveryStateFormWithoutFiles)
        } else {
            Log.e(TAG, "el formulario sin archivos es invalido")
        }
    }

    fun saveDeliveryStateFormWithFiles() {
        fetchData(this::saveDeliveryStateFormWithFiles)
    }
    
    private fun saveDeliveryStateFormWithFiles(accessToken: String?, idToken: String?, ex: AuthorizationException?) {
        if (ex != null) {
            Log.e(TAG, "Token refresh failed", ex)
            AuthStateManager.driverInfo = null
            if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {
                showAlert("Error", "Sesion Expirada", this::signOut)
            }
            return
        }
        Log.i(TAG, "trying to upload form files...")
        showLoader()
        val dataService: CclDataService? = CclClient.getInstance()?.create(
            CclDataService::class.java)
        if (dataService != null && accessToken != null) {
            var cont = 0
            formDataWithFiles?.data?.forEach { item ->
                if (item.value != null && item.value is MultipartBody.Part) {
                    Log.i(TAG, "uploading file ${item.name}:${item.value}...")
                    val urlSaveForm =
                        "delivery/state-history/upload-file/${savedFormId}?propertyName=${item.name}"
                    /*val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "com.tautech.cclapp.fileprovider",
                        item.value as File
                    )*/
                    doAsync {
                        showSnackbar("guardando ${item.name}: ")
                        try {
                            val callSaveForm = dataService.savePlanificationStateFormFile(urlSaveForm,
                                item.value as MultipartBody.Part,
                                viewModel.planification.value?.customerId,
                                "Bearer $accessToken")
                                .execute()
                            hideLoader()
                            //val responseSaveForm = callSaveForm.body()
                            Log.i(TAG, "save file ${item.name} response ${callSaveForm.code()}")
                            if (callSaveForm.code() == 200 || callSaveForm.code() == 201 || callSaveForm.code() == 202) {
                                if(cont == formDataWithFiles?.data?.size!! - 1){
                                    filesSaved = true
                                    finishActivity()
                                }
                            }
                        } catch(toe: SocketTimeoutException) {
                            hideLoader()
                            Log.e(TAG, "Network error when saving ${item.name} file", toe)
                            showAlert(getString(R.string.network_error_title), getString(R.string.network_error))
                        } catch (ioEx: IOException) {
                            hideLoader()
                            Log.e(TAG,
                                "Network error when saving ${item.name} file",
                                ioEx)
                            showAlert(getString(R.string.network_error_title), getString(R.string.network_error))
                        }
                    }
                } else {
                    showSnackbar(getString(R.string.invalid_file_for, item.name))
                }
                cont++
            }
        }
    }

    fun saveDeliveredItemList() {
        if (!deliveredItemList.isNullOrEmpty() && savedFormId != null) {
            fetchData(this::saveDeliveredItemList)
        } else {
            Log.e(TAG, "La lista de items entregados es invalida")
        }
    }

    private fun saveDeliveredItemList(accessToken: String?, idToken: String?, ex: AuthorizationException?) {
        if (ex != null) {
            Log.e(TAG, "Token refresh failed", ex)
            AuthStateManager.driverInfo = null
            if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {
                showAlert("Error", "Sesion Expirada", this::signOut)
            }
            return
        }
        Log.i(TAG, "trying to save delivered items...")
        showLoader()
        val dataService: CclDataService? = CclClient.getInstance()?.create(
            CclDataService::class.java)
        if (dataService != null && accessToken != null) {
            val url = "deliveredItems"
            doAsync {
                try {
                    val call = dataService.saveDeliveredItems(url,
                        deliveredItemList!!,
                        viewModel.planification.value?.customerId,
                        "Bearer $accessToken")
                        .execute()
                    hideLoader()
                    val response = call.body()
                    Log.i(TAG, "save delivered items response $response")
                    if (response != null) {
                        delivery?.detail?.forEach { deliveryLine ->
                            db?.deliveryLineDao()?.insert(deliveryLine)
                        }
                        viewModel.delivery.postValue(delivery)
                        savedItems = true
                        finishActivity()
                        showSnackbar(getString(R.string.delivered_items_saved))
                    }
                } catch(toe: SocketTimeoutException) {
                    hideLoader()
                    Log.e(TAG, "Network error when saving delivered items", toe)
                    showAlert(getString(R.string.network_error_title), getString(R.string.network_error))
                } catch (ioEx: IOException) {
                    hideLoader()
                    Log.e(TAG,
                        "Network error when saving delivered items",
                        ioEx)
                    showAlert(getString(R.string.network_error_title), getString(R.string.network_error))
                }
            }
        }
    }
    
    private fun saveDeliveryStateFormWithoutFiles(accessToken: String?, idToken: String?, ex: AuthorizationException?) {
        if (ex != null) {
            Log.e(TAG, "Token refresh failed", ex)
            AuthStateManager.driverInfo = null
            if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {
                showAlert("Error", "Sesion Expirada", this::signOut)
            }
            return
        }
        showLoader()
        val dataService: CclDataService? = CclClient.getInstance()?.create(
            CclDataService::class.java)
        if (dataService != null && accessToken != null) {
            val urlSaveForm = "delivery/${delivery?.id}/state-history"
            val formDataWithoutFiles = formDataWithoutFiles
            doAsync {
                showSnackbar(getString(R.string.saving_form))
                try {
                    val callSaveForm = dataService.savePlanificationStateForm(urlSaveForm,
                        this@ManageDeliveryActivity.formDataWithoutFiles!!,
                        viewModel.planification.value?.customerId,
                        "Bearer $accessToken")
                        .execute()
                    hideLoader()
                    val responseSaveForm = callSaveForm.body()
                    Log.i(TAG, "save state form response $responseSaveForm")
                    if (responseSaveForm != null) {
                        savedFormId = responseSaveForm
                        if (formDataWithFiles != null  && savedFormId != null) {
                            saveDeliveryStateFormWithFiles()
                        } else {
                            Log.e(TAG, "el formulario con archivos o el id del formulario guardado son invalidos")
                        }
                        saveDeliveredItemList()
                        delivery?.deliveryState = formDataWithoutFiles?.state
                        db?.deliveryDao()?.update(delivery!!)
                        viewModel.delivery.postValue(delivery)
                    }
                } catch(toe: SocketTimeoutException) {
                    hideLoader()
                    Log.e(TAG, "Network error when saving state form", toe)
                    showAlert(getString(R.string.network_error_title), getString(R.string.network_error))
                } catch (ioEx: IOException) {
                    hideLoader()
                    Log.e(TAG,
                        "Network error when saving state form",
                        ioEx)
                    showAlert(getString(R.string.network_error_title), getString(R.string.network_error))
                }
            }
        }
    }

    

    fun hideLoader() {
        runOnUiThread {
            checkDoneBtnStatus()
            progressBarGeneral?.visibility = View.GONE
            view_pager2?.visibility = View.VISIBLE
        }
    }

    fun showLoader() {
        runOnUiThread {
            doneBtn.isEnabled = false
            progressBarGeneral?.visibility = View.VISIBLE
            view_pager2?.visibility = View.GONE
        }
    }

    private fun showSnackbar(message: String) {
        runOnUiThread {
            Snackbar.make(contentView!!,
                message,
                Snackbar.LENGTH_SHORT)
                .show()
        }
    }

    private fun signOut() {
        // discard the authorization and token state, but retain the configuration and
        // dynamic client registration (if applicable), to save from retrieving them again.
        val currentState = mStateManager!!.current
        val clearedState = AuthState(currentState.authorizationServiceConfiguration!!)
        if (currentState.lastRegistrationResponse != null) {
            clearedState.update(currentState.lastRegistrationResponse)
        }
        mStateManager!!.replace(clearedState)
        val mainIntent = Intent(this, LoginActivity::class.java)
        mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK and Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(mainIntent)
        finish()
    }

    fun finishActivity(){
        if(savedFormId != null && savedItems == true && ((formDataWithFiles != null && filesSaved == true) || formDataWithFiles == null)){
            finish()
        }
    }
}