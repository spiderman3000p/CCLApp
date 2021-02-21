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
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.snackbar.Snackbar
import com.tautech.cclapp.R
import com.tautech.cclapp.classes.AuthStateManager
import com.tautech.cclapp.classes.Configuration
import com.tautech.cclapp.database.AppDatabase
import com.tautech.cclapp.interfaces.CclDataService
import com.tautech.cclapp.models.*
import com.tautech.cclapp.services.CclClient
import kotlinx.android.synthetic.main.activity_planification_detail.*
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import org.jetbrains.anko.contentView
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.json.JSONException
import retrofit2.Retrofit
import java.io.IOException
import java.net.SocketTimeoutException


class PlanificationDetailActivity : AppCompatActivity() {
    private var newState: String = ""
    val TAG = "PLANIFICATION_DETAIL_ACTIVITY"
    private var retrofitClient: Retrofit? = null
    var loadingData = false
    var planification: Planification? = null
    private var mAuthService: AuthorizationService? = null
    private var mStateManager: AuthStateManager? = null
    private var mConfiguration: Configuration? = null
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
            Log.i(TAG, "No hay autorizacion para el usuario")
            signOut()
            return
        }
        viewModel.planification.observe(this, Observer{_planification ->
            if (_planification != null) {
                planification = _planification
                invalidateOptionsMenu()
                loadStateFormDefinitions()
                if (!loadingData) {
                    loadingData = true
                    fetchPlanificationDataReq()
                }
            }
        })
        viewModel.deliveries.observe(this, Observer{_deliveries ->
            if(!_deliveries.isNullOrEmpty()){
                invalidateOptionsMenu()
            }
        })
        viewModel.deliveryLines.observe(this, Observer{_lines ->
            if(!_lines.isNullOrEmpty()){
                invalidateOptionsMenu()
            }
        })
        val extras = intent.extras
        if (extras != null) {
            // TODO obtener planificacion id de shared preferences y luego la planificacion de la BD
            if (extras.containsKey("planification")) {
                planification = extras.getSerializable("planification") as Planification
                Log.i(TAG, "planification recibido en extras ${planification?.id}")
            } else {
                Log.i(TAG, "no se recibio ninguna planificacion. enviando a planificaciones")
                finish()
            }
        } else {
            Log.i(TAG, "no se recibieron datos")
            finish()
        }
        /*if (savedInstanceState != null) {
            if (savedInstanceState.containsKey("planification")) {
                planification = savedInstanceState.getSerializable("planification") as Planification
                viewModel.planification.postValue(planification)
            }
        }*/
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume()...")
        loadPlanification(planification?.id)
    }

    private fun loadPlanification(planificationId: Long?){
        doAsync {
            val _planification = db?.planificationDao()?.getById(planificationId?.toInt()!!)
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
        mStateManager?.signOut(this)
        val mainIntent = Intent(this, LoginActivity::class.java)
        mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK and Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(mainIntent)
        finish()
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

    fun loadStateFormDefinitions() {
        if (viewModel.stateFormDefinitions.value == null || viewModel.stateFormDefinitions.value?.isNullOrEmpty() == true) {
            fetchData(this::loadStateFormDefinitions)
        }
    }

    private fun loadStateFormDefinitions(accessToken: String?, idToken: String?, ex: AuthorizationException?) {
        if (ex != null) {
            Log.e(TAG, "Token refresh failed when finalizing planification load", ex)
            AuthStateManager.driverInfo = null
            if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {
                showAlert("Error", "Sesion Expirada", this::signOut)
            }
            return
        }
        //https://{{hostname}}/stateFormDefinitions/search/findByCustomerId?customerId=2
        //val url = "stateFormDefinitions/search/findByCustomerId?customerId=${planification?.customerId}"
        //api/customers/stateConfing?customer-id=${planification?.customerId}
        val url = "api/customers/stateConfing?customer-id=${planification?.customerId}"
        //Log.i(TAG_PLANIFICATIONS, "constructed user endpoint: $userInfoEndpoint")
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
                    if (response != null && response.size > 0) {
                        try {
                            viewModel.stateFormDefinitions.postValue(response.toMutableList())
                            for (def in response) {
                                Log.i(TAG, "guardando en DB local definicion de ${def.deliveryState}")
                                db?.stateFormDefinitionDao()?.insert(def)
                                Log.i(TAG, "definicion de ${def.deliveryState} tiene ${def.formFieldList?.size ?: 0} fields")
                                if (!def.formFieldList.isNullOrEmpty()) {
                                    for (field in def.formFieldList!!) {
                                        Log.i(TAG, "guardando field: $field")
                                        field.formDefinitionId = def.id?.toInt()
                                        db?.stateFormFieldDao()?.insert(field)
                                    }
                                }
                            }
                        } catch (ex: SQLiteException) {
                            Log.e(TAG,
                                "Error guardando state form definitions en la BD local",
                                ex)
                            showAlert(getString(R.string.database_error),
                                getString(R.string.database_error_saving_state_form_definitions))
                        } catch (ex: SQLiteConstraintException) {
                            Log.e(TAG,
                                "Error guardando state form definitions en la BD local",
                                ex)
                            showAlert(getString(R.string.database_error),
                                getString(R.string.database_error_saving_state_form_definitions))
                        } catch (ex: Exception) {
                            Log.e(TAG,
                                "Error guardando state form definitions en la BD local",
                                ex)
                            showAlert(getString(R.string.database_error),
                                getString(R.string.database_error_saving_state_form_definitions))
                        }
                    }
                } catch(toe: SocketTimeoutException) {
                    Log.e(TAG, "Error de red cargando state form definitions", toe)
                    showAlert(getString(R.string.network_error_title), getString(R.string.network_error))
                } catch (ioEx: IOException) {
                    Log.e(TAG,
                        "Error de red cargando state form definitions",
                        ioEx)
                    showAlert(getString(R.string.network_error_title), getString(R.string.network_error))
                }
            }
        }
    }

    private fun fetchPlanificationData(
        accessToken: String?,
        idToken: String?,
        ex: AuthorizationException?,
    ) {
        if (ex != null) {
            Log.e(TAG, "Token refresh failed when fetching planification data", ex)
            if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {

            }
            showAlert("Sesion finalizada",
                "Su sesion ha expirado, debe iniciar sesion nuevamente", this::signOut)
            return
        }
        hideViews()
        showLoader()
        val url = "delivery/label/planifications"
        Log.i(TAG, "planification data endpoint: ${url}")
        val dataService: CclDataService? = CclClient.getInstance()?.create(
            CclDataService::class.java)
        var deliveryMap: HashMap<String, PlanificationLine>? = hashMapOf()
        if (dataService != null && accessToken != null) {
            doAsync {
                try {
                    Log.i(TAG, "fetching planification data for planification ${planification?.id}")
                    val call = dataService.getPlanificationLines(url, "Bearer $accessToken", arrayListOf(planification?.id!!)).execute()
                    val planifications = call.body()
                    if (planifications != null && planifications.size > 0) {
                        Log.i(TAG, "planificacion cargada de internet $planifications")
                        planification?.deliveredStats = planifications[0].deliveredStats
                        deliveryMap = planifications[0].deliveryMap // obviamente solicitamos un solo resultado
                        if (deliveryMap != null) {
                            parsePlanificationDeliveryMap(deliveryMap)
                        }
                        //viewModel.planification.postValue(planification)
                    }
                    loadingData = false
                    hideLoader()
                    showViews()
                } catch(toe: SocketTimeoutException) {
                    hideLoader()
                    showRetryMessage("Network error fetching user planification data",
                        this@PlanificationDetailActivity::fetchPlanificationDataReq)
                    showSnackbar("Fetching user planification data failed")
                    Log.e(TAG, "Network error when querying planification data endpoint", toe)
                } catch (ioEx: IOException) {
                    hideLoader()
                    showRetryMessage("Network error fetching user planification data",
                        this@PlanificationDetailActivity::fetchPlanificationDataReq)
                    showSnackbar("Fetching user planification data failed")
                    Log.e(TAG, "Network error when querying planification data endpoint", ioEx)
                } catch (jsonEx: JSONException) {
                    hideLoader()
                    showRetryMessage("Error parsing user planification data",
                        this@PlanificationDetailActivity::fetchPlanificationDataReq)
                    Log.e(TAG, "Failed to parse planification data response", jsonEx)
                    showSnackbar("Failed to parse planification data")
                } catch (e: Exception) {
                    hideLoader()
                    showRetryMessage("Fetching user planification data failed",
                        this@PlanificationDetailActivity::fetchPlanificationDataReq)
                    showSnackbar("Fetching planification data failed")
                    Log.e(TAG, "Unknown exception: ", e)
                }
            }
        }
    }

    fun fetchPlanificationDataReq() {
        if (viewModel.deliveries.value.isNullOrEmpty()) {
            Log.i(TAG, "recuperando deliveries y delivery lines desde la BD remota...")
            fetchData(this::fetchPlanificationData)
        } else if(planification != null) {
            Log.i(TAG, "recuperando deliveries y delivery lines desde la BD local...")
            doAsync {
                showLoader()
                val deliveries = db?.deliveryDao()?.getAllByPlanification(planification?.id?.toInt()!!)
                if (deliveries != null) {
                    viewModel.deliveries.postValue(deliveries.toMutableList())
                    val deliveryLines = db?.deliveryLineDao()?.getAllByPlanification(planification?.id?.toInt()!!)
                    if (deliveryLines != null) {
                        viewModel.deliveryLines.postValue(deliveryLines.toMutableList())
                    }
                }
                hideLoader()
            }
        }
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

    private fun parsePlanificationDeliveryMap(deliveryMap: java.util.HashMap<String, PlanificationLine>?) {
        if (deliveryMap != null) {
            var aux: DeliveryLine
            val deliveries: MutableList<PlanificationLine> = mutableListOf()
            val deliveryLines: MutableList<DeliveryLine> = mutableListOf()
            try {
                for ((deliveryKey, delivery) in deliveryMap) {
                    try {
                        delivery.planificationId = planification?.id
                        db?.deliveryDao()?.insert(delivery)
                        deliveries.add(delivery)
                        for (deliveryLine in delivery.detail) {
                            for (i in 0 until deliveryLine.quantity) {
                                aux = deliveryLine.copy(index = i,
                                    deliveryId = delivery.id,
                                    planificationId = planification?.id!!)
                                db?.deliveryLineDao()?.insert(aux)
                                deliveryLines.add(aux)
                            }
                        }
                    } catch (ex: SQLiteException) {
                        Log.e(TAG, "Error saving delivery to local database", ex)
                    } catch (ex: SQLiteConstraintException) {
                        Log.e(TAG,
                            "Error saving delivery to local database",
                            ex)
                    } catch (ex: Exception) {
                        Log.e(TAG,
                            "Error saving delivery to local database",
                            ex)
                    }
                }
                viewModel.deliveries.postValue(deliveries)
                viewModel.deliveryLines.postValue(deliveryLines)
                //val added = db?.deliveryLineDao()?.getAllByPlanification(planification?.id!!)
                //Log.i(TAG, "Delivery lines agregados a la BD local: $added")
            } catch (ex: SQLiteException) {
                Log.e(TAG, "Error saving delivery line to local database", ex)
                showAlert("Database Error", "Error saving delivery line to local database")
            } catch (ex: SQLiteConstraintException) {
                Log.e(TAG,
                    "Error saving delivery line to local database",
                    ex)
                showAlert("Database Error", "Error saving delivery line to local database")
            } catch (ex: Exception) {
                Log.e(TAG,
                    "Error saving delivery line to local database",
                    ex)
                showAlert("Database Error", "Error saving delivery line to local database")
            }
        }
    }

    fun showAlert(title: String, message: String) {
        runOnUiThread {
            val builder = AlertDialog.Builder(this)
            builder.setTitle(title)
            builder.setMessage(message)
            builder.setPositiveButton("Aceptar", null)
            val dialog: AlertDialog = builder.create();
            dialog.show();
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu_planification, menu)
        menu.findItem(R.id.startRoute).isVisible = false
        menu.findItem(R.id.endRoute).isVisible = false
        menu.findItem(R.id.cancelRoute).isVisible = false
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
            R.id.cancelRoute -> {
                askForChangeState("Cancelled")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        if(viewModel.deliveries.value != null && viewModel.deliveryLines.value?.size!! > 0) {
            when(planification?.state) {
                "Created" -> {
                    menu?.findItem(R.id.cancelRoute)?.isVisible = false
                    menu?.findItem(R.id.startRoute)?.isVisible = false
                    menu?.findItem(R.id.endRoute)?.isVisible = false
                }
                "Dispatched" -> {
                    menu?.findItem(R.id.cancelRoute)?.isVisible = true
                    menu?.findItem(R.id.startRoute)?.isVisible = true
                    menu?.findItem(R.id.endRoute)?.isVisible = false
                }
                "OnGoing" -> {
                    menu?.findItem(R.id.cancelRoute)?.isVisible = false
                    menu?.findItem(R.id.startRoute)?.isVisible = false
                    menu?.findItem(R.id.endRoute)?.isVisible = true
                }
                "Cancelled" -> {
                    menu?.findItem(R.id.cancelRoute)?.isVisible = false
                    menu?.findItem(R.id.startRoute)?.isVisible = false
                    menu?.findItem(R.id.endRoute)?.isVisible = false
                }
                "Complete" -> {
                    menu?.findItem(R.id.cancelRoute)?.isVisible = false
                    menu?.findItem(R.id.startRoute)?.isVisible = false
                    menu?.findItem(R.id.endRoute)?.isVisible = false
                }
            }
        } else {
            menu?.findItem(R.id.cancelRoute)?.isVisible = false
            menu?.findItem(R.id.startRoute)?.isVisible = false
            menu?.findItem(R.id.endRoute)?.isVisible = false
        }
        return super.onPrepareOptionsMenu(menu)
    }

    fun askForChangeState(state: String) {
        newState = state
        when(state) {
            "Cancelled" -> {
                showAlert(getString(R.string.cancel_planification), getString(R.string.cancel_planification_prompt), this::changePlanificationState)
            }
            "OnGoing" -> {
                showAlert(getString(R.string.start_route), getString(R.string.start_route_prompt), this::changePlanificationState)
            }
            "Complete" -> {
                showAlert(getString(R.string.complete_route), getString(R.string.complete_route_prompt), this::changePlanificationState)
            }
        }
    }

    fun changePlanificationState() {
        fetchData(this::changePlanificationState)
    }

    private fun changePlanificationState(accessToken: String?, idToken: String?, ex: AuthorizationException?) {
        if (ex != null) {
            Log.e(TAG, "Token refresh failed when finalizing planification load", ex)
            AuthStateManager.driverInfo = null
            if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {

            }
            showAlert("Error", "Sesion Expirada", this::signOut)
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
                    /*val call = dataService.finalizePlanificationLoad(url,
                        "Bearer $accessToken")
                        .execute()*/
                    val call = dataService.changePlanificationState(url,
                        "Bearer $accessToken")
                        .execute()
                    val response = call.body()
                    Log.i(TAG, "respuesta al cambiar estado de planificacion ${planification?.id}: ${response}")
                    hideLoader()
                    showViews()
                    planification?.state = newState
                    viewModel.planification.postValue(planification)
                    try {
                        db?.planificationDao()?.update(planification)
                    } catch (ex: SQLiteException) {
                        hideLoader()
                        Log.e(TAG,
                            "Error actualizando planificacion en la BD local",
                            ex)
                        showAlert(getString(R.string.database_error), getString(R.string.database_error_saving_planifications))
                    } catch (ex: SQLiteConstraintException) {
                        hideLoader()
                        Log.e(TAG,
                            "Error actualizando planificacion en la BD local",
                            ex)
                        showAlert(getString(R.string.database_error), getString(R.string.database_error_saving_planifications))
                    } catch (ex: Exception) {
                        hideLoader()
                        Log.e(TAG,
                            "Error actualizando planificacion en la BD local",
                            ex)
                        showAlert(getString(R.string.database_error), getString(R.string.database_error_saving_planifications))
                    }
                    Log.i(TAG, "finalize planification load response $response")
                } catch(toe: SocketTimeoutException) {
                    hideLoader()
                    Log.e(TAG, "Network error when finalizing planification load", toe)
                    showAlert(getString(R.string.network_error_title), getString(R.string.network_error))
                } catch (ioEx: IOException) {
                    hideLoader()
                    Log.e(TAG,
                        "Network error when finalizing planification load",
                        ioEx)
                    showAlert(getString(R.string.network_error_title), getString(R.string.network_error))
                } catch (jsonEx: JSONException) {
                    hideLoader()
                    Log.e(TAG, "Failed to parse finalizing planification response", jsonEx)
                    showAlert(getString(R.string.parsing_error_title), getString(R.string.parsing_error))
                }
            }
        }
    }

    private fun fetchData(callback: ((String?, String?, AuthorizationException?) -> Unit)) {
        Log.i(TAG, "Fetching data...$callback")
        try {
            mStateManager?.current?.performActionWithFreshTokens(mAuthService!!,
                callback)
        }catch (ex: AuthorizationException) {
            Log.e(TAG, "error fetching data", ex)
        }
    }

    fun showAlert(title: String, message: String, positiveCallback: (() -> Unit)? = null, negativeCallback: (() -> Unit)? = null) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("Aceptar", DialogInterface.OnClickListener{dialog, id ->
            if (positiveCallback != null) {
                positiveCallback()
            }
            dialog.dismiss()
        })
        builder.setNegativeButton("Cancelar", DialogInterface.OnClickListener{dialog, id ->
            if (negativeCallback != null) {
                negativeCallback()
            }
            dialog.dismiss()
        })
        if(!this.isDestroyed && !this.isFinishing) {
            val dialog: AlertDialog = builder.create()
            dialog.show()
        }
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