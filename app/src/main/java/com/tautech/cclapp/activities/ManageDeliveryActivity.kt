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
import com.google.android.gms.common.util.CrashUtils
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.internal.analytics.CrashlyticsOriginAnalyticsEventLogger
import com.google.firebase.crashlytics.internal.common.CrashlyticsCore
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport
import com.tautech.cclapp.R
import com.tautech.cclapp.activities.ui_delivery_detail.delivery_form.DeliveryFormFragment
import com.tautech.cclapp.adapters.SectionsPagerAdapter
import com.tautech.cclapp.classes.AuthStateManager
import com.tautech.cclapp.classes.CclUtilities
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
import kotlin.collections.ArrayList
import kotlin.math.absoluteValue

class ManageDeliveryActivity : AppCompatActivity() {
    private lateinit var sectionsPagerAdapter: SectionsPagerAdapter
    private var deliveryLinesLoaded: Boolean = false
    private var paymentsSaved: Boolean = false
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
    private lateinit var viewModel: ManageDeliveryActivityViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_delivery)
        Log.i(TAG, "onCreate()...")
        val viewPager: ViewPager = findViewById(R.id.view_pager2)
        val tabs: TabLayout = findViewById(R.id.tabs2)
        val _viewModel: ManageDeliveryActivityViewModel by viewModels()
        viewModel = _viewModel
        Log.i(TAG, "onCreate() viewModel deliveryLines: ${viewModel.deliveryLines.value}")
        Log.i(TAG, "onCreate() viewModel state: ${viewModel.currentDeliveryState.value}")
        sectionsPagerAdapter = SectionsPagerAdapter(this, supportFragmentManager, this::onDeliveryLineChangedCallback)
        viewPager.offscreenPageLimit = 3
        viewPager.adapter = sectionsPagerAdapter
        tabs.setupWithViewPager(viewPager)
        doneBtn.isEnabled = false

        doneBtn.setOnClickListener { view ->
            if (!isValidForm()){
                showSnackbar(getString(R.string.invalid_form))
            } else if (!isPaymentValid()) {
                showSnackbar(getString(R.string.invalid_payment))
            } else {
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
            CclUtilities.getInstance().showAlert(this,"Error", "La configuracion de sesion ha cambiado. Se cerrara su sesion", this::signOut)
            return
        }
        if (!mStateManager!!.current.isAuthorized) {
            CclUtilities.getInstance().showAlert(this,"Error", "Su sesion ha expirado", this::signOut)
            return
        }
        viewModel.currentDeliveryState.observe(this, Observer{ newState ->
            Log.i(TAG, "nuevo estado observado $newState")
            if (!deliveryLinesLoaded){
                loadDeliveryLines()
            }
            if(newState == "UnDelivered"){
                viewModel.showPaymentMethod.setValue(false)
            } else {
                viewModel.showPaymentMethod.setValue(true)
            }
            checkDoneBtnStatus()
        })
        viewModel.showPaymentMethod.observe(this, Observer{show ->
            Log.i(TAG, "showPaymentMethod recibido $show y estado es ${viewModel.currentDeliveryState.value}")
            if (!show || viewModel.currentDeliveryState.value == "UnDelivered") {
                Log.i(TAG, "Hay que ocultar la pestaña de pagos...hay ${tabs.tabCount} pestañas")
                if (tabs.tabCount == 3) {
                    Log.i(TAG, "Hay tres pestañas. removiendo pestaña de pago...")
                    showLoader()
                    tabs.removeTabAt(2)
                    val _sectionsPagerAdapter =
                        SectionsPagerAdapter(this, supportFragmentManager, this::onDeliveryLineChangedCallback,true)
                    viewPager.offscreenPageLimit = 2
                    viewPager.adapter = null
                    viewPager.adapter = _sectionsPagerAdapter
                    tabs.setupWithViewPager(viewPager)
                    hideLoader()
                } else {
                    Log.i(TAG, "No hay tres pestañas...")
                }
            } else if(show && viewModel.currentDeliveryState.value != "UnDelivered") {
                Log.i(TAG, "Hay que mostrar la pestaña de pagos...hay ${tabs.tabCount} pestañas")
                if (tabs.tabCount < 3) {
                    showLoader()
                    Log.i(TAG, "hay menos de 3 pestañas. agregando pestaña de pago...")
                    val _sectionsPagerAdapter = SectionsPagerAdapter(this, supportFragmentManager, this::onDeliveryLineChangedCallback)
                    viewPager.offscreenPageLimit = 3
                    viewPager.adapter = null
                    viewPager.adapter = _sectionsPagerAdapter
                    tabs.setupWithViewPager(viewPager)
                    hideLoader()
                } else {
                    Log.i(TAG, "Hay mas de dos pestañas...")
                }
            }
        })
        viewModel.payments.observe(this, {payments ->
            Log.i(TAG, "observed payments $payments")
            calculateTotals()
        })
        viewModel.deliveryLines.observe(this, Observer { deliveryLines ->
            Log.i(TAG, "observed delivery lines: $deliveryLines")
            calculateTotals()
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
                CclUtilities.getInstance().showAlert(this,getString(R.string.error), getString(R.string.no_planification_received))
                Log.e(TAG, "no se recibio ninguna planificacion")
                finish()
            }
            if (extras.containsKey("delivery")) {
                delivery = extras.getSerializable("delivery") as Delivery
                viewModel.delivery.setValue(delivery)
            } else {
                CclUtilities.getInstance().showAlert(this,getString(R.string.error), getString(R.string.no_delivery_received))
                Log.e(TAG, "no se recibio ninguna delivery")
                finish()
            }
            if (extras.containsKey("state")) {
                val newState = extras.getString("state") ?: ""
                Log.i(TAG, "estado recibido de actividad: $newState")
                if(newState.isEmpty()){
                    CclUtilities.getInstance().showAlert(this,getString(R.string.error), getString(R.string.no_state_received))
                    Log.e(TAG, "no se recibio ningun estado")
                    finish()
                } else {
                    viewModel.currentDeliveryState.setValue(newState)
                }
            } else {
                Log.e(TAG, "no se recibio ningun estado")
                CclUtilities.getInstance().showAlert(this,getString(R.string.error), getString(R.string.no_state_received))
                finish()
            }
        } else {
            Log.i(TAG, "no se recibieron datos")
            CclUtilities.getInstance().showAlert(this,getString(R.string.error), getString(R.string.no_data_received))
            finish()
        }
    }

    fun checkDoneBtnStatus(){
        runOnUiThread {
            doneBtn.isEnabled = listOf("UnDelivered", "Delivered", "Partial").contains(viewModel.currentDeliveryState.value)
        }
    }

    private fun onDeliveryLineChangedCallback(deliveryLine: DeliveryLine){
        Log.i(TAG, "cantidad de items cambiada para el delivery line recibido: $deliveryLine")
        val changedDeliveryLine = viewModel.deliveryLines.value?.find {
            it.id == deliveryLine.id
        }
        Log.i(TAG, "delivery lines en el viewmodel: ${viewModel.deliveryLines.value}")
        changedDeliveryLine?.delivered = deliveryLine.delivered
        Log.i(TAG, "delivery line cambiado en el viewmodel: $changedDeliveryLine")
        calculateTotals()
        Log.i(TAG, "estado inicial del viewmodel: ${viewModel.currentDeliveryState.value}")
        val state = getFinalFutureState()
        Log.i(TAG, "estado final generado: $state")
        viewModel.currentDeliveryState.setValue(state)
        viewModel.currentDeliveryState.postValue(state)
        Log.i(TAG, "estado final del viewmodel: ${viewModel.currentDeliveryState.value}")
    }

    fun calculateTotals(){
        Log.i(TAG, "calculating totals...")
        val utilities = CclUtilities.getInstance()
        val totalToPay = (viewModel.deliveryLines.value?.fold(0.0, { total, deliveryLine ->
            total + if (deliveryLine.delivered > 0) ((deliveryLine.price / deliveryLine.quantity) * deliveryLine.delivered) else 0.0
        })  ?: 0.0)
        val totalPaid = (viewModel.payments.value?.fold(0.0, { total, payment ->
            total + (payment.detail?.amount ?: 0.0)
        })  ?: 0.0)
        val pending = totalToPay - totalPaid
        Log.i(TAG, "total paid: $totalPaid")
        Log.i(TAG, "total to pay: $totalToPay")
        Log.i(TAG, "total pending: $pending")
        totalToPayTv.text = utilities.formatCurrencyNumber(totalToPay)
        paidAmountTv.text = utilities.formatCurrencyNumber(totalPaid)
        totalPendingTv.text = utilities.formatCurrencyNumber(if(pending > 0.1) pending else 0.0)
        totalExceedingTv.text = utilities.formatCurrencyNumber(if(pending < 0.0) pending.absoluteValue else 0.0)
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
                    "deliveryLines cargadas de la bd local para la guia ${delivery?.deliveryId} y el estado ${viewModel.currentDeliveryState.value}: $deliveryLines")
                when (viewModel.currentDeliveryState.value) {
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
                calculateTotals()
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
                CclUtilities.getInstance().showAlert(this,"Sesion expirada", "Su sesion ha expirado", this::signOut)
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
                    Log.i(TAG, "respuesta al cargar customer details: $response")
                    viewModel.showPaymentMethod.postValue(response?.showPaymentMethod ?: false)
                } catch(toe: SocketTimeoutException) {
                    Log.e(TAG, "Error de red cargando customer details", toe)
                    CclUtilities.getInstance().showAlert(this@ManageDeliveryActivity,getString(R.string.network_error_title), getString(R.string.network_error))
                } catch (ioEx: IOException) {
                    Log.e(TAG,
                        "Error de red cargando customer details",
                        ioEx)
                    CclUtilities.getInstance().showAlert(this@ManageDeliveryActivity,getString(R.string.network_error_title), getString(R.string.network_error))
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
                CclUtilities.getInstance().showAlert(this,"Sesion expirada", "Su sesion ha expirado", this::signOut)
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
                    CclUtilities.getInstance().showAlert(this@ManageDeliveryActivity,getString(R.string.network_error_title), getString(R.string.network_error))
                } catch (ioEx: IOException) {
                    Log.e(TAG,
                        "Error de red cargando customer details",
                        ioEx)
                    CclUtilities.getInstance().showAlert(this@ManageDeliveryActivity,getString(R.string.network_error_title), getString(R.string.network_error))
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
        CclUtilities.getInstance().showAlert(this,getString(R.string.important), getString(R.string.manage_delivery_prompt), this::changeDeliveryState)
    }

    fun changeDeliveryState() {
        val fragment = DeliveryFormFragment.getInstance()
        if (fragment?.isValidForm() == true && isPaymentValid()) {
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
                CclUtilities.getInstance().showAlert(this,"Error", "Los datos de formulario o la lista de items son invalidos")
            }
        }
    }

    fun generateDeliveredItemList(): List<DeliveredItemToUpload>? {
        return ManageDeliveryItemsFragment.getInstance()?.filteredData?.map {deliveryLine ->
            DeliveredItemToUpload(
                quantityDelivered = deliveryLine.delivered,
                amount = (deliveryLine.price / deliveryLine.quantity) * deliveryLine.delivered,
                deliveryLineId = deliveryLine.id,
                planificationId = planification?.id!!
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

    private fun saveDeliveryStateFormWithFiles() {
        Log.i(TAG, "trying to upload form files...")
        showLoader()
        formDataWithFiles?.forEach { item ->
            if (item.value != null && item.value is File) {
                MyWorkerManagerService.enqueSingleFileUpload(this, item, "form", savedFormId, planification?.customerId)
            } else {
                showSnackbar(getString(R.string.invalid_file_for, item.name))
            }
        }
        filesSaved = true
        finishActivity()
    }

    private fun saveDeliveryPayments() {
        Log.i(TAG, "trying to upload form files...")
        showLoader()
        // guardar todos de una vez
        MyWorkerManagerService.enqueUploadSinglePaymentArrayWork(this, viewModel.payments.value!!, delivery?.deliveryId!!)
        paymentsSaved = true
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
                CclUtilities.getInstance().showAlert(this,"Sesion expirada", "Su sesion ha expirado", this::signOut)
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
                        db?.deliveryLineDao()?.insertAll(viewModel.deliveryLines.value!!)
                        val deliveredUnitsCount = deliveredItemList?.fold(0, { totalDelivered, deliveryLine ->
                            totalDelivered + deliveryLine.quantityDelivered
                        }) ?: 0
                        //val newDeliveredUnitsCount = ((delivery?.totalDelivered ?: 0) - deliveredUnitsCount).absoluteValue
                        delivery?.totalDelivered = (delivery?.totalDelivered ?: 0) + deliveredUnitsCount
                        db?.deliveryDao()?.update(delivery!!)
                        // actualizamos los contadores de planificacion correspondientes
                        if (viewModel.currentDeliveryState.value != "UnDelivered") {
                            planification?.totalDeliveredUnits = (planification?.totalDeliveredUnits ?: 0) + deliveredUnitsCount
                        }
                        Log.i(TAG, "estado de la guia antes de actualizar la planificacion: ${viewModel.currentDeliveryState.value}")
                        when(viewModel.currentDeliveryState.value) {
                            "Delivered" -> planification?.totalDelivered = (planification?.totalDelivered ?: 0) + 1
                            "Partial" -> planification?.totalPartial = (planification?.totalPartial ?: 0) + 1
                            "UnDelivered" -> planification?.totalUndelivered = (planification?.totalUndelivered ?: 0) + 1
                        }
                        //planification?.totalDeliveredUnits = (planification?.totalDeliveredUnits ?: 0) + newDeliveredUnitsCount
                        Log.i(TAG, "cambios en planificacion por guardar a la BD: $planification")
                        db?.planificationDao()?.update(planification!!)
                        //viewModel.delivery.postValue(delivery)
                        savedItems = true
                        showSnackbar(getString(R.string.delivered_items_saved))
                        finishActivity()
                    }
                } catch(toe: SocketTimeoutException) {
                    hideLoader()
                    Log.e(TAG, "Network error when saving delivered items", toe)
                    CclUtilities.getInstance().showAlert(this@ManageDeliveryActivity,getString(R.string.network_error_title), getString(R.string.network_error))
                } catch (ioEx: IOException) {
                    hideLoader()
                    Log.e(TAG,
                        "Network error when saving delivered items",
                        ioEx)
                    CclUtilities.getInstance().showAlert(this@ManageDeliveryActivity,getString(R.string.network_error_title), getString(R.string.network_error))
                } catch(ex: Exception){
                    FirebaseCrashlytics.getInstance().recordException(ex)
                    hideLoader()
                    Log.e(TAG,"Error desconocido al itentar guardar items", ex)
                    CclUtilities.getInstance().showAlert(this@ManageDeliveryActivity,getString(R.string.error), getString(R.string.unknown_error))
                }
            }
        }
    }
    
    private fun saveDeliveryStateFormWithoutFiles(accessToken: String?, idToken: String?, ex: AuthorizationException?) {
        if (ex != null) {
            Log.e(TAG, "ocurrio una excepcion mientras se guardaba el formulario", ex)
            if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {
                CclUtilities.getInstance().showAlert(this,"Sesion expirada", "Su sesion ha expirado", this::signOut)
            }
            return
        }
        showLoader()
        val dataService: CclDataService? = CclClient.getInstance()?.create(
            CclDataService::class.java)
        if (dataService != null && accessToken != null) {
            val urlSaveForm = "delivery/${delivery?.deliveryId}/state-history"
            formDataWithoutFiles?.state = viewModel.currentDeliveryState.value!!
            doAsync {
                try {
                    val callSaveForm = dataService.savePlanificationStateForm(urlSaveForm,
                        this@ManageDeliveryActivity.formDataWithoutFiles!!,
                        planification?.customerId!!,
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
                            if (viewModel.showPaymentMethod.value == true && viewModel.currentDeliveryState.value != "UnDelivered" && viewModel.payments.value?.isNotEmpty() == true) {
                                saveDeliveryPayments()
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
                    CclUtilities.getInstance().showAlert(this@ManageDeliveryActivity,getString(R.string.network_error_title), getString(R.string.network_error))
                } catch (ioEx: IOException) {
                    hideLoader()
                    Log.e(TAG,
                        "Network error when saving state form",
                        ioEx)
                    CclUtilities.getInstance().showAlert(this@ManageDeliveryActivity,getString(R.string.network_error_title), getString(R.string.network_error))
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
                    CclUtilities.getInstance().showAlert(this,"Error",
                        "No se pudo finalizar la sesion",
                        this::signOut)
                } else {
                    mStateManager?.signOut(this)
                }
            } else {
                Log.e(TAG, "Error al intentar finalizar sesion", ex)
                CclUtilities.getInstance().showAlert(this,"Error",
                    "No se pudo finalizar la sesion remota",
                    this::signOut)
            }
        }
    }

    fun finishActivity(){
        if(isFormAndItemsCompleted() && isPaymentCompleted()){
            val data = ArrayList<DeliveryLine>()
            data.addAll(viewModel.deliveryLines.value!!)
            setResult(RESULT_OK, Intent().putExtra("deliveredLines", data))
            finishAndRemoveTask()
        }
    }

    private fun isFormAndItemsCompleted(): Boolean{
        // si se guardo el formulario, los items y los archivos del formulario en caso de que aplique...
        return savedFormId != null && savedItems && ((!formDataWithFiles.isNullOrEmpty() && filesSaved) || formDataWithFiles.isNullOrEmpty())
    }

    private fun isPaymentCompleted(): Boolean {
        // si el pago es requerido y el estado es valido, o el pago es requerido pero el estado invalido, o el pago no es requerido
        return ((viewModel.showPaymentMethod.value == true && viewModel.currentDeliveryState.value != "UnDelivered" && paymentsSaved) ||
                (viewModel.showPaymentMethod.value == true && viewModel.currentDeliveryState.value == "UnDelivered") ||
                viewModel.showPaymentMethod.value == false)
    }

    private fun isPaymentValid(): Boolean {
        // si el pago no es requerido, o el pago es requerido y el estado valido, o el pago es requerido pero el estado es invalido
        val totalToPay = (viewModel.deliveryLines.value?.fold(0.0, { total, deliveryLine ->
            total + if (deliveryLine.delivered > 0) ((deliveryLine.price / deliveryLine.quantity) * deliveryLine.delivered) else 0.0
        })  ?: 0.0)
        val totalPaid = (viewModel.payments.value?.fold(0.0, { total, payment ->
            total + (payment.detail?.amount ?: 0.0)
        })  ?: 0.0)
        val paymentExceeded = (totalPaid - totalToPay) >= 1
        val paymentNotEnough = (totalToPay - totalPaid) >= 1
        Log.i(TAG, "totalPaid - totalToPay: ${totalPaid - totalToPay}")
        Log.i(TAG, "totalToPay - totalPaid: ${totalToPay - totalPaid}")
        Log.i(TAG, "totalPaid: $totalPaid")
        Log.i(TAG, "totalToPay: $totalToPay")
        val invalidPayment = paymentExceeded || paymentNotEnough
        Log.i(TAG, "valid payment: $invalidPayment")
        return (viewModel.showPaymentMethod.value == false ||
                (viewModel.showPaymentMethod.value == true && viewModel.currentDeliveryState.value != "UnDelivered" &&
                viewModel.payments.value?.isNotEmpty() == true
                && !invalidPayment) ||
                (viewModel.showPaymentMethod.value == true && viewModel.currentDeliveryState.value == "UnDelivered"))
    }

    private fun isValidForm(): Boolean {
        val fragment = DeliveryFormFragment.getInstance()
        return (viewModel.stateFormDefinition.value != null && fragment != null && fragment.isValidForm())
    }

    override fun onDestroy() {
        super.onDestroy()
        sectionsPagerAdapter.clear()
    }
}