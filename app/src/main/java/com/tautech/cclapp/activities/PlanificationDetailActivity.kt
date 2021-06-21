package com.tautech.cclapp.activities

import android.content.DialogInterface
import android.content.Intent
import android.database.sqlite.*
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tautech.cclapp.R
import com.tautech.cclapp.classes.AuthStateManager
import com.tautech.cclapp.classes.Configuration
import com.tautech.cclapp.activities.legalization.PreviewPlanificationDialog
import com.tautech.cclapp.classes.CclUtilities
import com.tautech.cclapp.database.AppDatabase
import com.tautech.cclapp.interfaces.CclDataService
import com.tautech.cclapp.models.Planification
import com.tautech.cclapp.services.CclClient
import kotlinx.android.synthetic.main.activity_planification_detail.*
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.EndSessionResponse
import org.jetbrains.anko.contentView
import org.jetbrains.anko.doAsync
import org.json.JSONException
import retrofit2.Retrofit
import java.io.IOException
import java.net.SocketTimeoutException


class PlanificationDetailActivity : AppCompatActivity() {
    companion object {
        val DELIVERY_DETAIL: Int = 1
    }
    private var newState: String = ""
    val TAG = "PLANIFICATION_DETAIL_ACTIVITY"
    private var retrofitClient: Retrofit? = null
    var loadingData = false
    var planification: Planification? = null
    private var mStateManager: AuthStateManager? = null
    var db: AppDatabase? = null
    private val viewModel: PlanificationDetailActivityViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_planification_detail)
        val navView: BottomNavigationView = findViewById(R.id.nav_view2)
        val navController = findNavController(R.id.nav_host_fragment_urban)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(setOf(
            R.id.navigation_deliveries, R.id.navigation_finished, R.id.navigation_resume_urban))
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        supportFragmentManager.beginTransaction()
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
            CclUtilities.getInstance().showAlert(this,"Sesion expirada", "Su sesion ha expirado", this::signOut)
            return
        }
        viewModel.planification.observe(this, Observer{_planification ->
            if (_planification != null) {
                planification = _planification
                invalidateOptionsMenu()
                if (!loadingData) {
                    loadingData = true
                    if (viewModel.deliveries.value.isNullOrEmpty()) {
                        doAsync {
                            fetchPlanificationDataReq()
                        }
                    } else {
                        doAsync {
                            loadDeliveriesFromLocal()
                        }
                    }
                }
                if (viewModel.stateFormDefinitions.value.isNullOrEmpty()) {
                    doAsync {
                        fetchData(this@PlanificationDetailActivity::loadStateFormDefinitions)
                    }
                } else {
                    doAsync {
                        loadStateFormDefinitionsFromLocal()
                    }
                }
            }
        })
        viewModel.deliveries.observe(this, Observer{_deliveries ->
            if(!_deliveries.isNullOrEmpty()){
                invalidateOptionsMenu()
            }
        })
        val extras = intent.extras
        if (extras != null) {
            // TODO obtener planificacion id de shared preferences y luego la planificacion de la BD
            if (extras.containsKey("planification")) {
                planification = extras.getSerializable("planification") as Planification
                viewModel.planification.postValue(planification)
                Log.i(TAG, "planification recibido en extras ${planification?.id}")
            } else {
                Log.i(TAG, "no se recibio ninguna planificacion. enviando a planificaciones")
                finish()
            }
        } else {
            Log.i(TAG, "no se recibieron datos")
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume()...")
        mStateManager?.revalidateSessionData(this)
    }

    private fun loadPlanification(planificationId: Long){
        doAsync {
            val _planification = db?.planificationDao()?.getById(planificationId)
            viewModel.planification.postValue(_planification)
            Log.i(TAG, "planification loaded from local DB: $_planification")
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
        Log.i(TAG, "onActivityResult() requestCode: $requestCode, resultCode: $resultCode")
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
                    val mainIntent = Intent(this,
                        LoginActivity::class.java)
                    mainIntent.flags =
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(mainIntent)
                    finishAndRemoveTask()
                }
            } else {
                Log.e(TAG, "Error al intentar finalizar sesion", ex)
                CclUtilities.getInstance().showAlert(this,"Error",
                    "No se pudo finalizar la sesion remota",
                    this::signOut)
            }
        }
        if (requestCode == DELIVERY_DETAIL && planification != null) {
            Log.i(TAG, "onActivityResult() viniendo de detalle de planificacion")
            doAsync{
                val _planification = db?.planificationDao()?.getById(planification?.id!!)
                viewModel.planification.postValue(_planification)
                Log.i(TAG, "planification loaded from local DB: $_planification")
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    fun hideViews() {
        runOnUiThread {
            if (layout2 != null) {
                layout2.visibility = View.GONE
            }
        }
    }

    fun showViews() {
        runOnUiThread {
            if (layout2 != null) {
                layout2.visibility = View.VISIBLE
            }
        }
    }

    private fun loadStateFormDefinitions(accessToken: String?, idToken: String?, ex: AuthorizationException?) {
        if (ex != null) {
            Log.e(TAG, "ocurrio una excepcion mientras se recuperaban las definiciones de formularios", ex)
            if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {
                CclUtilities.getInstance().showAlert(this,"Sesion expirada", "Su sesion ha expirado", this::signOut)
            }
            return
        }
        val url = "api/customers/stateConfing?customer-id=${planification?.customerId}"
        val dataService: CclDataService? = CclClient.getInstance()?.create(
            CclDataService::class.java)
        if (dataService != null && accessToken != null) {
            doAsync {
                try {
                    val call = dataService.getStateFormDefinitions(url,
                        "Bearer $accessToken")
                        .execute()
                    val response = call.body()
                    Log.i(TAG, "respuesta al cargar form definitions: ${response}")
                    if (!response.isNullOrEmpty()) {
                        try {
                            viewModel.stateFormDefinitions.postValue(response.toMutableList())
                            Log.i(TAG, "guardando en DB local definiciones")
                            if(viewModel.planification.value?.customerId != null) {
                                db?.stateFormDefinitionDao()
                                    ?.deleteAllByCustomer(viewModel.planification.value?.customerId!!)
                            }
                            db?.stateFormDefinitionDao()?.insertAll(response)
                            val allFields = response.flatMap{def ->
                                def.formFieldList?.forEach{field ->
                                    field.formDefinitionId = def.id
                                }
                                def.formFieldList ?: listOf()
                            }
                            db?.stateFormFieldDao()?.deleteAll()
                            if (allFields.isNotEmpty()) {
                                Log.i(TAG, "all form fields: $allFields")
                                db?.stateFormFieldDao()?.insertAll(allFields)
                            }
                        } catch (ex: SQLiteException) {
                            Log.e(TAG,
                                "Error guardando state form definitions en la BD local",
                                ex)
                            CclUtilities.getInstance().showAlert(this@PlanificationDetailActivity,getString(R.string.database_error),
                                getString(R.string.database_error_saving_state_form_definitions))
                        } catch (ex: SQLiteConstraintException) {
                            Log.e(TAG,
                                "Error guardando state form definitions en la BD local",
                                ex)
                            CclUtilities.getInstance().showAlert(this@PlanificationDetailActivity,getString(R.string.database_error),
                                getString(R.string.database_error_saving_state_form_definitions))
                        } catch (ex: Exception) {
                            FirebaseCrashlytics.getInstance().recordException(ex)
                            Log.e(TAG,
                                "Error guardando state form definitions en la BD local",
                                ex)
                            CclUtilities.getInstance().showAlert(this@PlanificationDetailActivity,getString(R.string.database_error),
                                getString(R.string.database_error_saving_state_form_definitions))
                        }
                    }
                } catch(toe: SocketTimeoutException) {
                    Log.e(TAG, "Error de red cargando state form definitions", toe)
                    CclUtilities.getInstance().showAlert(this@PlanificationDetailActivity,getString(R.string.network_error_title), getString(R.string.network_error))
                } catch (ioEx: IOException) {
                    Log.e(TAG,
                        "Error de red cargando state form definitions",
                        ioEx)
                    CclUtilities.getInstance().showAlert(this@PlanificationDetailActivity,getString(R.string.network_error_title), getString(R.string.network_error))
                }
            }
        }
    }

    private fun loadDeliveriesFromLocal(){
        loadingData = true
        showLoader()
        hideViews()
        doAsync {
            try {
                Log.i(TAG, "fetching planification ${planification?.id} deliveries from local DB")
                val deliveries = db?.deliveryDao()?.getAllByPlanification(planification?.id!!)
                if (!deliveries.isNullOrEmpty()) {
                    Log.i(TAG, "deliveries cargadas de BD local $deliveries")
                    viewModel.deliveries.postValue(deliveries.toMutableList())
                }
            } catch(ex: Exception) {
                FirebaseCrashlytics.getInstance().recordException(ex)
                Log.e(TAG, "Excepcion al cargar deliveries de la BD local", ex)
            } finally {
                loadingData = false
                hideLoader()
                showViews()
            }
        }
    }

    private fun loadStateFormDefinitionsFromLocal(){
        doAsync {
            try {
                Log.i(TAG, "fetching state form defs from local DB")
                val definitions = db?.stateFormDefinitionDao()?.getAllByCustomer(planification?.customerId)
                if (!definitions.isNullOrEmpty()) {
                    Log.i(TAG, "definitions cargadas de BD local $definitions")
                    viewModel.stateFormDefinitions.postValue(definitions.toMutableList())
                }
            } catch(ex: Exception) {
                FirebaseCrashlytics.getInstance().recordException(ex)
                Log.e(TAG, "Excepcion al cargar definitions de la BD local", ex)
            }
        }
    }

    private fun fetchPlanificationData(
        accessToken: String?,
        idToken: String?,
        ex: AuthorizationException?,
    ) {
        if (ex != null) {
            Log.e(TAG, "ocurrio una excepcion mientras se recuperaban datos de la planificacion", ex)
            if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {
                CclUtilities.getInstance().showAlert(this,"Sesion expirada", "Su sesion ha expirado", this::signOut)
            }
            return
        }
        hideViews()
        showLoader()
        val url = "planificationDeliveryVO1s/search/findByPlanificationId?planificationId=${planification?.id}"
        Log.i(TAG, "planification data endpoint: ${url}")
        val dataService: CclDataService? = CclClient.getInstance()?.create(
            CclDataService::class.java)
        if (dataService != null && accessToken != null) {
            doAsync {
                try {
                    Log.i(TAG, "fetching planification ${planification?.id} deliveries")
                    val call = dataService.getPlanificationLines(url, "Bearer $accessToken").execute()
                    val response = call.body()
                    showViews()
                    if (response != null && response._embedded.planificationDeliveryVO1s.isNotEmpty()) {
                        Log.i(TAG, "deliveries cargadas de internet ${response._embedded.planificationDeliveryVO1s}")
                        viewModel.deliveries.postValue(response._embedded.planificationDeliveryVO1s)
                        db?.deliveryDao()?.insertAll(response._embedded.planificationDeliveryVO1s)
                    }
                } catch(toe: SocketTimeoutException) {
                    showRetryMessage("Network error fetching user planification data",
                        this@PlanificationDetailActivity::fetchPlanificationDataReq)
                    showSnackbar("Fetching user planification data failed")
                    Log.e(TAG, "Network error when querying planification data endpoint", toe)
                } catch (ioEx: IOException) {
                    showRetryMessage("Network error fetching user planification data",
                        this@PlanificationDetailActivity::fetchPlanificationDataReq)
                    showSnackbar("Fetching user planification data failed")
                    Log.e(TAG, "Network error when querying planification data endpoint", ioEx)
                } catch (jsonEx: JSONException) {
                    showRetryMessage("Error parsing user planification data",
                        this@PlanificationDetailActivity::fetchPlanificationDataReq)
                    Log.e(TAG, "Failed to parse planification data response", jsonEx)
                    showSnackbar("Failed to parse planification data")
                } catch (e: Exception) {
                    showRetryMessage("Fetching user planification data failed",
                        this@PlanificationDetailActivity::fetchPlanificationDataReq)
                    showSnackbar("Fetching planification data failed")
                    Log.e(TAG, "Unknown exception: ", e)
                } finally {
                    loadingData = false
                    hideLoader()
                }
            }
        }
    }

    fun fetchPlanificationDataReq() {
        Log.i(TAG, "recuperando deliveries y delivery lines desde la BD remota...")
        fetchData(this::fetchPlanificationData)
    }

    private fun showRetryMessage(message: String, callback: () -> Unit) {
        runOnUiThread{
            messageHomeTv2?.text = message
            retryBtn2?.setOnClickListener {
                callback.invoke()
            }
            retryLayout2?.visibility = View.VISIBLE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu_planification, menu)
        menu.findItem(R.id.startRoute).isVisible = false
        menu.findItem(R.id.endRoute).isVisible = false
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        return when (item.itemId) {
            R.id.startRoute -> {
                askForChangeState("OnGoing")
                true
            }
            R.id.endRoute -> {
                askForChangeState("Complete")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        when(planification?.state) {
            "Created" -> {
                menu?.findItem(R.id.startRoute)?.isVisible = false
                menu?.findItem(R.id.endRoute)?.isVisible = false
            }
            "Dispatched" -> {
                menu?.findItem(R.id.startRoute)?.isVisible = true
                menu?.findItem(R.id.endRoute)?.isVisible = false
            }
            "OnGoing" -> {
                menu?.findItem(R.id.startRoute)?.isVisible = false
                menu?.findItem(R.id.endRoute)?.isVisible = true
            }
            "Cancelled" -> {
                menu?.findItem(R.id.startRoute)?.isVisible = false
                menu?.findItem(R.id.endRoute)?.isVisible = false
            }
            "Complete" -> {
                menu?.findItem(R.id.startRoute)?.isVisible = false
                menu?.findItem(R.id.endRoute)?.isVisible = false
            }
        }
        return true
    }

    fun askForChangeState(state: String) {
        newState = state
        when(state) {
            "Cancelled" -> {
                CclUtilities.getInstance().showAlert(this,getString(R.string.cancel_planification), getString(R.string.cancel_planification_prompt), this::changePlanificationState)
            }
            "OnGoing" -> {
                CclUtilities.getInstance().showAlert(this,getString(R.string.start_route), getString(R.string.start_route_prompt), this::changePlanificationState)
            }
            "Complete" -> {
                CclUtilities.getInstance().showAlert(this,getString(R.string.complete_route), getString(R.string.complete_route_prompt), this::showFinalizationPreview)
            }
        }
    }

    fun showFinalizationPreview(){
        if(planification == null){
            return
        }
        PreviewPlanificationDialog.display(supportFragmentManager,
            planification?.id!!,
            this::changePlanificationState)
    }

    fun changePlanificationState() {
        fetchData(this::changePlanificationState)
    }

    private fun changePlanificationState(accessToken: String?, idToken: String?, ex: AuthorizationException?) {
        if (ex != null) {
            Log.e(TAG, "ocurrio una excepcion al cambiar estado de planificacion", ex)
            if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {
                CclUtilities.getInstance().showAlert(this,"Sesion expirada", "Su sesion ha expirado", this::signOut)
            }
            return
        }
        hideViews()
        showLoader()
        showSnackbar("Solicitando cambio de estado...")
        val url = "planification/${planification?.id}/changeState?newState=$newState"
        //Log.i(TAG_PLANIFICATIONS, "constructed user endpoint: $userInfoEndpoint")
        val dataService: CclDataService? = CclClient.getInstance()?.create(
            CclDataService::class.java)
        if (dataService != null && accessToken != null) {
            doAsync {
                try {
                    val call = dataService.changePlanificationState(url,
                        "Bearer $accessToken")
                        .execute()
                    val responseCode = call.code()
                    Log.i(TAG, "respuesta al cambiar estado de planificacion ${planification?.id}: ${responseCode}")
                    if(responseCode == 200 || responseCode == 201) {
                        showViews()
                        planification?.state = newState
                        viewModel.planification.postValue(planification)
                        fetchPlanificationDataReq()
                        try {
                            db?.planificationDao()?.update(planification!!)
                        } catch (ex: SQLiteException) {
                            Log.e(TAG,
                                "Error actualizando planificacion en la BD local",
                                ex)
                            CclUtilities.getInstance().showAlert(this@PlanificationDetailActivity,
                                getString(R.string.database_error),
                                getString(R.string.database_error_saving_planifications))
                        } catch (ex: SQLiteConstraintException) {
                            Log.e(TAG,
                                "Error actualizando planificacion en la BD local",
                                ex)
                            CclUtilities.getInstance().showAlert(this@PlanificationDetailActivity,
                                getString(R.string.database_error),
                                getString(R.string.database_error_saving_planifications))
                        } catch (ex: Exception) {
                            FirebaseCrashlytics.getInstance().recordException(ex)
                            Log.e(TAG,
                                "Error actualizando planificacion en la BD local",
                                ex)
                            CclUtilities.getInstance().showAlert(this@PlanificationDetailActivity,
                                getString(R.string.database_error),
                                getString(R.string.database_error_saving_planifications))
                        }
                    } else {
                        CclUtilities.getInstance().showAlert(this@PlanificationDetailActivity,
                            getString(R.string.error),
                            getString(R.string.error_changing_planification_state))
                    }
                } catch(toe: SocketTimeoutException) {
                    Log.e(TAG, "Network error when finalizing planification load", toe)
                    CclUtilities.getInstance().showAlert(this@PlanificationDetailActivity,getString(R.string.network_error_title), getString(R.string.network_error))
                } catch (ioEx: IOException) {
                    Log.e(TAG,
                        "Network error when finalizing planification load",
                        ioEx)
                    CclUtilities.getInstance().showAlert(this@PlanificationDetailActivity,getString(R.string.network_error_title), getString(R.string.network_error))
                } catch (jsonEx: JSONException) {
                    Log.e(TAG, "Failed to parse finalizing planification response", jsonEx)
                    CclUtilities.getInstance().showAlert(this@PlanificationDetailActivity,getString(R.string.parsing_error_title), getString(R.string.parsing_error))
                } finally {
                    hideLoader()
                }
            }
        }
    }

    private fun fetchData(callback: ((String?, String?, AuthorizationException?) -> Unit)) {
        Log.i(TAG, "oning data...$callback")
        mStateManager?.current?.performActionWithFreshTokens(mStateManager?.mAuthService!!, callback)
    }

    fun hideLoader() {
        runOnUiThread {
            progressBar3.visibility = View.GONE
            retryLayout2?.visibility = View.GONE
        }
    }

    fun showLoader() {
        runOnUiThread{
            progressBar3.visibility = View.VISIBLE
            retryLayout2?.visibility = View.GONE
        }
    }

    private fun showSnackbar(message: String) {
        runOnUiThread {
            if(!this.isDestroyed && !this.isFinishing) {
                Snackbar.make(contentView!!,
                    message,
                    Snackbar.LENGTH_SHORT)
                    .show()
            }
        }
    }
}