package com.tautech.cclapp.activities

import android.content.DialogInterface
import android.content.Intent
import android.database.sqlite.SQLiteAccessPermException
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.viewpager.widget.ViewPager
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.tautech.cclapp.R
import com.tautech.cclapp.activities.ui_delivery_detail.delivery_form.DeliveryFormFragment
import com.tautech.cclapp.activities.ui_delivery_detail.delivery_payment.DeliveryPaymentFragment
import com.tautech.cclapp.adapters.SectionsPagerAdapter
import com.tautech.cclapp.classes.AuthStateManager
import com.tautech.cclapp.classes.Configuration
import com.tautech.cclapp.database.AppDatabase
import com.tautech.cclapp.interfaces.CclDataService
import com.tautech.cclapp.models.*
import com.tautech.cclapp.services.CclClient
import com.tautech.cclapp.services.MyWorkerManagerService
import kotlinx.android.synthetic.main.activity_manage_delivery.*
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.EndSessionResponse
import org.jetbrains.anko.contentView
import org.jetbrains.anko.doAsync
import retrofit2.Retrofit
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException

class ManageDeliveryActivity : AppCompatActivity() {
    private var deliveryLinesLoaded: Boolean = false
    private var paymentDetailsSaved: Boolean = false
    private var paymentDetails: DeliveryPaymentDetail? = null
    private var filesSaved: Boolean = false
    private var savedItems: Boolean = false
    private var deliveredItemList: List<DeliveredItemToUpload>? = null
    private var savedFormId: Long? = null
    val TAG = "MANAGE_DELIVERY_ACTIVITY"
    var planification: Planification? = null
    var delivery: Delivery? = null
    var formDataWithoutFiles: StateForm? = null
    var formDataWithFiles: ArrayList<Item>? = null
    private var retrofitClient: Retrofit? = null
    private var mStateManager: AuthStateManager? = null
    var db: AppDatabase? = null
    private val viewModel: ManageDeliveryActivityViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_delivery)
        val viewPager: ViewPager = findViewById(R.id.view_pager2)
        val tabs: TabLayout = findViewById(R.id.tabs2)
        val sectionsPagerAdapter = SectionsPagerAdapter(this, supportFragmentManager)
        viewPager.offscreenPageLimit = 3
        viewPager.adapter = sectionsPagerAdapter
        tabs.setupWithViewPager(viewPager)
        doneBtn.isEnabled = false
        doneBtn.setOnClickListener { view ->
            val fragment = DeliveryFormFragment.getInstance()
            val fragment2 = DeliveryPaymentFragment.getInstance()
            //supportFragmentManager.findFragmentByTag("DeliveryForm") as DeliveryFormFragment?
            val validForm = fragment?.isValidForm()
            if (fragment != null && validForm == true && (viewModel.showPaymentMethod.value == false ||
               (viewModel.showPaymentMethod.value == true && fragment2?.isValidForm() == true))) {
                askForChangeState()
            }
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        retrofitClient = CclClient.getInstance()
        mStateManager = AuthStateManager.getInstance(this)
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
            showAlert("Error", "La configuracion de sesion ha cambiado. Se cerrara su sesion", this::signOut)
            return
        }
        if (!mStateManager!!.current.isAuthorized) {
            showAlert("Error", "Su sesion ha expirado", this::signOut)
            return
        }
        viewModel.state.observe(this, Observer{newState ->
            Log.i(TAG, "nuevo estado observado $newState")
            //doAsync {
                /*Log.i(TAG, "buscando definiciones en la BD local para el estado $newState...")
                val foundStateFormDefinition = db?.stateFormDefinitionDao()?.getAllByStateAndCustomer(newState, planification?.customerId!!)
                if (!foundStateFormDefinition.isNullOrEmpty()) {
                    Log.i(TAG, "found state form definition: $foundStateFormDefinition")
                    foundStateFormDefinition[0].formFieldList = db?.stateFormDefinitionDao()?.getFields(foundStateFormDefinition[0].id!!)
                    Log.i(TAG, "found fieldList: ${foundStateFormDefinition[0].formFieldList}")
                    viewModel.stateFormDefinition.postValue(foundStateFormDefinition[0])
                } else {
                    Log.e(TAG, "form definitions not found for state $newState and customer ${planification?.customerId}")
                    viewModel.stateFormDefinition.postValue(null)
                }*/
                if (!deliveryLinesLoaded){
                    loadDeliveryLines()
                }
                checkDoneBtnStatus()
            //}
        })
        viewModel.showPaymentMethod.observe(this, Observer{show ->
            Log.i(TAG, "showPaymentMethod recibido $show")
            if (!show) {
                Log.i(TAG, "removiendo pestaña de pago...")
                tabs.removeTabAt(2)
                val _sectionsPagerAdapter = SectionsPagerAdapter(this, supportFragmentManager, true)
                viewPager.offscreenPageLimit = 2
                viewPager.adapter = _sectionsPagerAdapter
                tabs.setupWithViewPager(viewPager)
            }
        })
        viewModel.changedDeliveryLine.observe(this, Observer {deliveryLine ->
            Log.i(TAG, "cantidad de items cambiada para el delivery line: $deliveryLine")
            val changedDeliveryLine = viewModel.deliveryLines.value?.find {
                it.id == deliveryLine.id
            }
            changedDeliveryLine?.delivered = deliveryLine.delivered
            val state = getFinalFutureState()
            Log.i(TAG, "estado final generado: $state")
            viewModel.state.setValue(state)
        })
        val extras = intent.extras
        if (extras != null) {
            // TODO obtener planificacion id de shared preferences y luego la planificacion de la BD
            if (extras.containsKey("planification")) {
                planification = extras.getSerializable("planification") as Planification
                fetchCustomerDetails()
                fetchPaymentMethods()
                viewModel.planification.setValue(planification)
            } else {
                showAlert(getString(R.string.error), getString(R.string.no_planification_received))
                Log.e(TAG, "no se recibio ninguna planificacion")
                finish()
            }
            if (extras.containsKey("delivery")) {
                delivery = extras.getSerializable("delivery") as Delivery
                viewModel.delivery.setValue(delivery)
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
                    viewModel.state.setValue(newState)
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
    }

    fun checkDoneBtnStatus(){
        runOnUiThread {
            doneBtn.isEnabled = listOf("UnDelivered", "Delivered", "Partial").contains(viewModel.state.value)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume()...")
        mStateManager?.revalidateSessionData(this)
        showLoader()
        doAsync {
            if (planification == null) {
                loadPlanification(planification?.id)
            }
            if (delivery == null) {
                loadDelivery(delivery?.deliveryId)
            }
            /*if (viewModel.state.value != null && viewModel.deliveryLines.value.isNullOrEmpty()){
                loadDeliveryLines()
            }*/
            hideLoader()
        }
    }

    fun loadPlanification(planificationId: Long?){
        val _planification = db?.planificationDao()?.getById(planificationId!!)
        viewModel.planification.postValue(_planification)
        Log.i(TAG, "planification loaded from local DB: $_planification")
    }

    fun loadDelivery(deliveryId: Long?){
        Log.i(TAG, "loadDelivery from id: $deliveryId")
        val _delivery = db?.deliveryDao()?.getById(deliveryId)
        if (_delivery != null) {
            Log.i(TAG, "loadDelivery delivery loaded from bd local: $_delivery")
            viewModel.delivery.postValue(_delivery)
        }
        Log.i(TAG, "delivery loaded from local DB: $_delivery")
    }

    fun loadDeliveryLines(){
        doAsync {
            val deliveryLines =
                db?.deliveryDao()?.getGroupedLines(delivery?.deliveryId!!) ?: listOf()
            if (!deliveryLines.isNullOrEmpty()) {
                Log.i(TAG,
                    "deliveryLines cargadas de la bd local para la guia ${delivery?.deliveryId} y el estado ${viewModel.state.value}: $deliveryLines")
                when (viewModel.state.value) {
                    "Delivered" -> {
                        deliveryLines.forEach { deliveryLine ->
                            deliveryLine.delivered = deliveryLine.quantity
                        }
                    }
                    "UnDelivered" -> {
                        deliveryLines.forEach { deliveryLine ->
                            deliveryLine.delivered = 0
                        }
                    }
                    else -> {
                        deliveryLines.forEach { deliveryLine ->
                            deliveryLine.delivered = deliveryLine.quantity
                        }
                    }
                }
                Log.i(TAG, "enviando delivery lines: $deliveryLines")
                deliveryLinesLoaded = true
                viewModel.deliveryLines.postValue(deliveryLines.toMutableList())
            }
        }
    }

    fun fetchCustomerDetails() {
        fetchData(this::fetchCustomerDetails)
    }

    private fun fetchCustomerDetails(accessToken: String?, idToken: String?, ex: AuthorizationException?) {
        if (ex != null) {
            Log.e(TAG, "ocurrio una excepcion mientras se recuperaban los detalles del customer", ex)
            if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {
                showAlert("Sesion expirada", "Su sesion ha expirado", this::signOut)
            }
            return
        }
        Log.i(TAG, "loading customer details...")
        val url = "customers/${planification?.customerId}"
        Log.i(TAG, "built endpoint: $url")
        val dataService: CclDataService? = CclClient.getInstance()?.create(
            CclDataService::class.java)
        if (dataService != null && accessToken != null) {
            doAsync {
                try {
                    val call = dataService.getCustomer(url,
                        "Bearer $accessToken")
                        .execute()
                    val response = call.body()
                    Log.i(TAG, "respuesta al cargar customer details: ${response}")
                    if (response != null) {
                        Log.i(TAG, "enviando showPaymentMethod ${response.showPaymentMethod}")
                        viewModel.showPaymentMethod.postValue(response.showPaymentMethod)
                    } else {
                        Log.i(TAG, "la respuesta es null, enviando showPaymentMethod false")
                        viewModel.showPaymentMethod.postValue(false)
                    }
                } catch(toe: SocketTimeoutException) {
                    Log.e(TAG, "Error de red cargando customer details", toe)
                    showAlert(getString(R.string.network_error_title), getString(R.string.network_error))
                } catch (ioEx: IOException) {
                    Log.e(TAG,
                        "Error de red cargando customer details",
                        ioEx)
                    showAlert(getString(R.string.network_error_title), getString(R.string.network_error))
                }
            }
        }
    }

    fun fetchPaymentMethods() {
        fetchData(this::fetchPaymentMethods)
    }

    private fun fetchPaymentMethods(accessToken: String?, idToken: String?, ex: AuthorizationException?) {
        if (ex != null) {
            Log.e(TAG, "ocurrio una excepcion mientras se recuperaban metodos de pago", ex)
            if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {
                showAlert("Sesion expirada", "Su sesion ha expirado", this::signOut)
            }
            return
        }
        Log.i(TAG, "loading payment methods...")
        val dataService: CclDataService? = CclClient.getInstance()?.create(
            CclDataService::class.java)
        if (dataService != null && accessToken != null) {
            doAsync {
                try {
                    val call = dataService.getPaymentMethods(
                        "Bearer $accessToken")
                        .execute()
                    val response = call.body()
                    Log.i(TAG, "respuesta al cargar metodos de pago: ${response}")
                    if (!response?._embedded?.paymentMethods.isNullOrEmpty()) {
                        viewModel.paymentMethods.postValue(response?._embedded?.paymentMethods)
                    }
                } catch(toe: SocketTimeoutException) {
                    Log.e(TAG, "Error de red cargando metodos de pago", toe)
                    showAlert(getString(R.string.network_error_title), getString(R.string.network_error))
                } catch (ioEx: IOException) {
                    Log.e(TAG,
                        "Error de red cargando customer details",
                        ioEx)
                    showAlert(getString(R.string.network_error_title), getString(R.string.network_error))
                }
            }
        }
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
        val fragment = DeliveryFormFragment.getInstance()
            //supportFragmentManager.findFragmentByTag("DeliveryForm") as DeliveryFormFragment?
        val fragment2 = DeliveryPaymentFragment.getInstance()
        if (fragment?.isValidForm() == true && (viewModel.showPaymentMethod.value == false ||
           (viewModel.showPaymentMethod.value == true && fragment2?.isValidForm() == true))) {
            formDataWithoutFiles = fragment.generateFormDataWithoutFiles()
            formDataWithFiles = fragment.generateFormDataWithFiles()
            deliveredItemList = generateDeliveredItemList()
            Log.i(TAG, "form data without files generated: $formDataWithoutFiles")
            Log.i(TAG, "form data with files generated: $formDataWithFiles")
            Log.i(TAG, "item list generated: $deliveredItemList")
            if(formDataWithoutFiles != null && deliveredItemList != null) {
                saveDeliveryStateFormWithoutFiles()
            } else {
                Log.e(TAG, "form or item list are null")
                showAlert("Error", "Los datos de formulario o la lista de items son invalidos")
            }
        }/* else {
            Toast.makeText(this, getString(R.string.no_state_form_found), Toast.LENGTH_SHORT).show()
        }*/
    }

    fun generateDeliveredItemList(): List<DeliveredItemToUpload>? {
        return ManageDeliveryItemsFragment.getInstance()?.filteredData?.map {deliveryLine ->
            DeliveredItemToUpload(
                quantity = deliveryLine.delivered,
                price = deliveryLine.price,
                deliveryLineId = deliveryLine.id
            )
        }
    }

    private fun fetchData(callback: ((String?, String?, AuthorizationException?) -> Unit)) {
        Log.i(TAG, "Fetching user planifications...")
        try {
            mStateManager?.current?.performActionWithFreshTokens(mStateManager?.mAuthService!!,
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

    fun saveDeliveryPaymentMethod() {
        if (paymentDetails != null) {
            fetchData(this::saveDeliveryPaymentMethod)
        } else {
            Log.e(TAG, "el formulario sin archivos es invalido")
        }
    }
    
    private fun saveDeliveryStateFormWithFiles() {
        Log.i(TAG, "trying to upload form files...")
        showLoader()
        formDataWithFiles?.forEach { item ->
            if (item.value != null && item.value is File) {
                MyWorkerManagerService.enqueSingleFileUpload(this, item, savedFormId, viewModel.planification.value?.customerId)
            } else {
                showSnackbar(getString(R.string.invalid_file_for, item.name))
            }
        }
        filesSaved = true
        finishActivity()
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
            Log.e(TAG, "ocurrio una excepcion mientras se guardaban los items entregados", ex)
            if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {
                showAlert("Sesion expirada", "Su sesion ha expirado", this::signOut)
            }
            return
        }
        Log.i(TAG, "trying to save delivered items...")
        showLoader()
        val dataService: CclDataService? = CclClient.getInstance()?.create(
            CclDataService::class.java)
        if (dataService != null && accessToken != null && deliveredItemList != null) {
            doAsync {
                try {
                    val call = dataService.saveDeliveredItems(
                        deliveredItemList,
                        "Bearer $accessToken")
                        .execute()
                    hideLoader()
                    Log.i(TAG, "save delivered items response code ${call.code()}")
                    if (call.code() == 200) {
                        //db?.deliveryLineDao()?.insertAll(viewModel.deliveredItems.value!!)
                        //viewModel.deliveryLines.postValue(viewModel.deliveredItems.value!!)
                        delivery?.totalDelivered = (delivery?.totalDelivered ?: 0) +
                        (viewModel.deliveryLines.value?.fold(0, { totalDelivered, deliveryLine ->
                            totalDelivered + deliveryLine.delivered
                        })  ?: 0)
                        db?.deliveryDao()?.update(delivery!!)
                        viewModel.delivery.postValue(delivery)
                        savedItems = true
                        showSnackbar(getString(R.string.delivered_items_saved))
                        finishActivity()
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
                } catch(ex: Exception){
                    hideLoader()
                    Log.e(TAG,"Error desconocido al itentar guardar items", ex)
                    showAlert(getString(R.string.error), getString(R.string.unknown_error))
                }
            }
        }
    }
    
    private fun saveDeliveryStateFormWithoutFiles(accessToken: String?, idToken: String?, ex: AuthorizationException?) {
        if (ex != null) {
            Log.e(TAG, "ocurrio una excepcion mientras se guardaba el formulario", ex)
            if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {
                showAlert("Sesion expirada", "Su sesion ha expirado", this::signOut)
            }
            return
        }
        showLoader()
        val dataService: CclDataService? = CclClient.getInstance()?.create(
            CclDataService::class.java)
        if (dataService != null && accessToken != null) {
            val urlSaveForm = "delivery/${delivery?.deliveryId}/state-history"
            formDataWithoutFiles?.state = viewModel.state.value!!
            doAsync {
                try {
                    val callSaveForm = dataService.savePlanificationStateForm(urlSaveForm,
                        this@ManageDeliveryActivity.formDataWithoutFiles!!,
                        viewModel.planification.value?.customerId,
                        "Bearer $accessToken")
                        .execute()
                    hideLoader()
                    val responseSaveForm = callSaveForm.body()
                    Log.i(TAG, "save state form response $responseSaveForm with code ${callSaveForm.code()}")
                    if (responseSaveForm != null) {
                        savedFormId = responseSaveForm
                        if (formDataWithFiles != null  && savedFormId != null) {
                            saveDeliveryStateFormWithFiles()
                            saveDeliveredItemList()
                            if (viewModel.showPaymentMethod.value == true) {
                                val fragment2 = DeliveryPaymentFragment.getInstance()
                                if (fragment2 != null) {
                                    paymentDetails = fragment2.generateDeliveryPaymentDetail()
                                    if (viewModel.showPaymentMethod.value == true && fragment2.isValidForm()) {
                                        saveDeliveryPaymentMethod()
                                    }
                                }
                            }
                            delivery?.deliveryState = formDataWithoutFiles?.state
                            db?.deliveryDao()?.update(delivery!!)
                            viewModel.delivery.postValue(delivery)
                        } else {
                            Log.e(TAG, "el formulario con archivos o el id del formulario guardado son invalidos")
                        }
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

    private fun saveDeliveryPaymentMethod(accessToken: String?, idToken: String?, ex: AuthorizationException?) {
        if (ex != null) {
            Log.e(TAG, "ocurrio una excepcion mientras se guardaba el metodo de pago", ex)
            if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {
                showAlert("Sesion expirada", "Su sesion ha expirado", this::signOut)
            }
            return
        }
        showLoader()
        val dataService: CclDataService? = CclClient.getInstance()?.create(
            CclDataService::class.java)
        if (dataService != null && accessToken != null) {
            val urlSaveForm = "paymentDetails"
            doAsync {
                showSnackbar(getString(R.string.saving_form))
                try {
                    val callSaveForm = dataService.saveDeliveryPaymentDetails(
                        this@ManageDeliveryActivity.paymentDetails!!,
                        "Bearer $accessToken")
                        .execute()
                    hideLoader()
                    Log.i(TAG, "save payment details response ${callSaveForm.code()}")
                    if (callSaveForm.code() == 201) {
                        paymentDetailsSaved = true
                    }
                } catch(toe: SocketTimeoutException) {
                    hideLoader()
                    Log.e(TAG, "Network error when saving payment details", toe)
                    showAlert(getString(R.string.network_error_title), getString(R.string.network_error))
                } catch (ioEx: IOException) {
                    hideLoader()
                    Log.e(TAG,
                        "Network error when saving payment details",
                        ioEx)
                    showAlert(getString(R.string.network_error_title), getString(R.string.network_error))
                }
            }
        }
    }

    @kotlin.jvm.Throws(IOException::class)
    fun getFinalFutureState(): String{
        val totalDeliveredItems = viewModel.deliveryLines.value?.fold(0, { totalDelivered, deliveryLine ->
            totalDelivered + deliveryLine.delivered
        })  ?: 0
        Log.i(TAG, "totalDeliveredItems: $totalDeliveredItems")
        Log.i(TAG, "filteredData: ${ManageDeliveryItemsFragment.getInstance()?.filteredData}")
        if (totalDeliveredItems == viewModel.delivery.value?.totalQuantity) {
            return "Delivered"
        }
        if (totalDeliveredItems == 0) {
            return "UnDelivered"
        }
        if (totalDeliveredItems > 0 && totalDeliveredItems < viewModel.delivery.value?.totalQuantity ?: 0) {
            return "Partial"
        }
        throw (IOException("Error: Unknown final state for delivery ${viewModel.delivery.value?.deliveryId}"))
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
        if (mStateManager != null && mStateManager?.current != null && mStateManager?.mConfiguration?.redirectUri != null &&
            mStateManager?.current?.idToken != null && mStateManager?.current?.authorizationServiceConfiguration != null) {
            val endSessionRequest = EndSessionRequest.Builder(
                mStateManager?.current?.authorizationServiceConfiguration!!,
                mStateManager?.current?.idToken!!,
                mStateManager?.mConfiguration?.redirectUri!!
            ).build()
            if (endSessionRequest != null) {
                val authService = AuthorizationService(this)
                val endSessionIntent = authService.getEndSessionRequestIntent(endSessionRequest)
                startActivityForResult(endSessionIntent, AuthStateManager.RC_END_SESSION)
            } else {
                showSnackbar("Error al intentar cerrar sesion")
            }
        } else {
            Log.i(TAG,
                "mStateManager?.mConfiguration?.redirectUri: ${mStateManager?.mConfiguration?.redirectUri}")
            Log.i(TAG, "mStateManager?.current?.idToken: ${mStateManager?.current?.idToken}")
            Log.i(TAG,
                "mStateManager?.current?.authorizationServiceConfiguration: ${mStateManager?.current?.authorizationServiceConfiguration}")
            showSnackbar("Error al intentar cerrar sesion")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AuthStateManager.RC_END_SESSION) {
            val resp: EndSessionResponse = EndSessionResponse.fromIntent(data!!)!!
            val ex = AuthorizationException.fromIntent(data)
            Log.i(TAG, "logout response: $resp")
            if (resp != null) {
                if (ex != null) {
                    Log.e(TAG, "Error al intentar finalizar sesion", ex)
                    showAlert("Error",
                        "No se pudo finalizar la sesion",
                        this::signOut)
                } else {
                    mStateManager?.signOut(this)
                }
            } else {
                Log.e(TAG, "Error al intentar finalizar sesion", ex)
                showAlert("Error",
                    "No se pudo finalizar la sesion remota",
                    this::signOut)
            }
        }
    }

    fun finishActivity(){
        if(savedFormId != null && savedItems && ((!formDataWithFiles.isNullOrEmpty() && filesSaved) || formDataWithFiles.isNullOrEmpty())){
            val data = ArrayList<DeliveryLine>()
            data.addAll(viewModel.deliveryLines.value!!)
            setResult(RESULT_OK, Intent().putExtra("deliveredLines", data))
            finishAndRemoveTask()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        //viewModel.clear() crea un ciclo infinito
    }
}