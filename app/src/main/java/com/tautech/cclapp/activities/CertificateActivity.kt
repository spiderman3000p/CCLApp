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
import androidx.work.*
import com.google.android.material.snackbar.Snackbar
import com.tautech.cclapp.R
import com.tautech.cclapp.classes.AuthStateManager
import com.tautech.cclapp.classes.UploadFailedCertificationsWorker
import com.tautech.cclapp.database.AppDatabase
import com.tautech.cclapp.interfaces.CclDataService
import com.tautech.cclapp.models.*
import com.tautech.cclapp.services.CclClient
import com.tautech.cclapp.services.MyWorkerManagerService
import kotlinx.android.synthetic.main.activity_main.*
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import org.jetbrains.anko.contentView
import org.jetbrains.anko.doAsync
import org.json.JSONException
import retrofit2.Retrofit
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit


class CertificateActivity : AppCompatActivity() {
    private var newState: String = ""
    val TAG = "MAIN_ACTIVITY"
    val KEY_PLANIFICATION_INFO = "planification"
    private var retrofitClient: Retrofit? = null
    var planification: Planification? = null
    private var mAuthService: AuthorizationService? = null
    private var mStateManager: AuthStateManager? = null
    private var mConfiguration: com.tautech.cclapp.classes.Configuration? = null
    private var routeStarted: Boolean = false
    var db: AppDatabase? = null
    private val viewModel: CertificateActivityViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val navView: BottomNavigationView = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.nav_host_fragment)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(setOf(
            R.id.navigation_scan,
            R.id.navigation_scanned,
            R.id.navigation_pending,
            R.id.navigation_resume))
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        retrofitClient = CclClient.getInstance()
        mStateManager = AuthStateManager.getInstance(this)
        mConfiguration = com.tautech.cclapp.classes.Configuration.getInstance(this)
        val config = com.tautech.cclapp.classes.Configuration.getInstance(this)
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
            if (_planification!= null) {
                planification = _planification
                invalidateOptionsMenu()
                Log.i(TAG, "Planification cargada en observer: ${planification?.id}")
            }
        })

        val extras = intent.extras
        if (extras != null) {
            // TODO obtener planificacion id de shared preferences y luego la planificacion de la BD
            if (extras.containsKey("planification")) {
                planification = extras.getSerializable("planification") as Planification
                viewModel.planification.postValue(planification)
                doAsync {
                    fetchData(this@CertificateActivity::fetchPlanificationLines)
                }
            } else {
                Log.i(TAG, "no se recibio ninguna planificacion. enviando a planificaciones")
                finish()
            }
        } else {
            Log.i(TAG, "no se recibieron datos")
            finish()
        }
        MyWorkerManagerService.uploadFailedCertifications(this)
    }

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        // user info is retained to survive activity restarts, such as when rotating the
        // device or switching apps. This isn't essential, but it helps provide a less
        // jarring UX when these events occur - data does not just disappear from the view.
        if (viewModel.planification.value != null) {
            state.putSerializable(KEY_PLANIFICATION_INFO, viewModel.planification.value)
        }
    }

    override fun onDestroy(){
        super.onDestroy()

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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun fetchCertifiedLines(accessToken: String?, idToken: String?, ex: AuthorizationException?) {
        if (ex != null) {
            Log.e(TAG, "Token refresh failed when fetching certified lines", ex)
            if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {
                runOnUiThread {
                    showAlert("Error", "Sesion Expirada", this::signOut)
                }
            }
            return
        }
        Log.i(TAG, "fetching certified delivery lines for planification ${planification?.id}...")
        val url = "planificationCertifications/search/findByPlanificationId?planificationId=${planification?.id}"
        //Log.i(TAG_PLANIFICATIONS, "constructed user endpoint: $userInfoEndpoint")
        val dataService: CclDataService? = CclClient.getInstance()?.create(
            CclDataService::class.java)
        if (dataService != null && accessToken != null) {
            try {
                showLoader()
                val call = dataService.getPlanificationsCertifiedLines(url,
                    "Bearer $accessToken")
                    .execute()
                val response = call.body()
                //showViews()
                hideLoader()
                Log.i(TAG, "Fetched certified lines: ${response?._embedded?.planificationCertifications}")
                if (!response?._embedded?.planificationCertifications.isNullOrEmpty()) {
                    try {
                        db?.certificationDao()?.deleteAllByPlanification(planification?.id!!)
                        db?.certificationDao()?.insertAll(response?._embedded?.planificationCertifications!!)
                        Log.i(TAG, "Se guardaron ${response?._embedded?.planificationCertifications?.size} certificaciones en la BD local")
                        Log.i(TAG, "Buscando certificaciones para la planificacion ${planification?.id} en la coleccion de pendingDeliveryLines ${viewModel.pendingDeliveryLines.value}...")
                        val certifiedDeliveryLines = mutableListOf<DeliveryLine>()
                        for(certified in response?._embedded?.planificationCertifications!!) {
                            val certifiedDeliveryLine = viewModel.pendingDeliveryLines.value?.find {
                                it.id == certified.deliveryLineId && it.deliveryId == certified.deliveryId && it.index == certified.index
                            }
                            if(certifiedDeliveryLine != null){
                                Log.i(TAG, "se encontro el delivery line en la coleccion de pendientes. removiendolo de la coleccion de pendientes. ${certifiedDeliveryLine}")
                                viewModel.pendingDeliveryLines.value?.remove(certifiedDeliveryLine)
                                if(viewModel.certifiedDeliveryLines.value?.contains(certifiedDeliveryLine) == false) {
                                    Log.i(TAG, "agregandolo en la collecion de certificados")
                                    certifiedDeliveryLines.add(certifiedDeliveryLine)
                                } else {
                                    Log.i(TAG, "el delivery line certificado ${certifiedDeliveryLine.id} ya existe en la coleccion de certificados. No se agregara a la coleccion")
                                }
                            } else {
                                Log.e(TAG, "no se encontro el delivery line certificado en la coleccion de pendientes ${certified}")
                            }
                        }
                        if (!certifiedDeliveryLines.isNullOrEmpty()) {
                            Log.i(TAG, "Se encontraron ${certifiedDeliveryLines.size} delivery lines certificados en la base de datos local para la planificacion ${planification?.id}")
                            val existingCertifications = viewModel.certifiedDeliveryLines.value
                            Log.i(TAG, "lineas certificadas existentes antes de agregar nuevas: $existingCertifications")
                            existingCertifications?.addAll(certifiedDeliveryLines)
                            viewModel.certifiedDeliveryLines.postValue(existingCertifications)
                            viewModel.pendingDeliveryLines.postValue(viewModel.pendingDeliveryLines.value)
                        } else {
                            Log.i(TAG, "No se encontraron delivery lines certificados en la base datos remota")
                        }
                    } catch (ex: SQLiteException) {
                        Log.e(TAG,
                            "Error actualizando planificacion en la BD local",
                            ex)
                        showAlert(getString(R.string.database_error),
                                getString(R.string.database_error_saving_planifications))
                    } catch (ex: SQLiteConstraintException) {
                        Log.e(TAG,
                            "Error actualizando planificacion en la BD local",
                            ex)
                        showAlert(getString(R.string.database_error),
                                getString(R.string.database_error_saving_planifications))
                    } catch (ex: Exception) {
                        Log.e(TAG,
                            "Error actualizando planificacion en la BD local",
                            ex)
                        showAlert(getString(R.string.database_error),
                                getString(R.string.database_error_saving_planifications))
                    }
                } else {
                    Log.i(TAG, "No hay lineas certificadas en la BD remota")
                }
            } catch(toe: SocketTimeoutException) {
                hideLoader()
                Log.e(TAG, "Network error when finalizing planification load", toe)
            } catch (ioEx: IOException) {
                hideLoader()
                Log.e(TAG,
                    "Network error when finalizing planification load",
                    ioEx)
            } catch (jsonEx: JSONException) {
                hideLoader()
                Log.e(TAG, "Failed to parse finalizing planification response", jsonEx)
            }
        }
    }

    fun hideViews() {
        runOnUiThread {
            if (layout != null) {
                layout.visibility = View.GONE
            }
        }
    }

    fun showViews() {
        runOnUiThread {
            if (layout != null) {
                layout.visibility = View.VISIBLE
            }
        }
    }

    private fun fetchPlanificationLines(
        accessToken: String?,
        idToken: String?,
        ex: AuthorizationException?,
    ) {
        if (ex != null) {
            Log.e(TAG, "Token refresh failed when fetching planification lines", ex)
            if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {
                showAlert("Sesion finalizada",
                        "Su sesion ha expirado, debe iniciar sesion nuevamente", this::signOut)
            }
            return
        }
        val url = "delivery/label/planifications"
        Log.i(TAG, "planification lines endpoint: ${url}")
        val dataService: CclDataService? = CclClient.getInstance()?.create(
            CclDataService::class.java)
        var deliveryMap: HashMap<String, PlanificationLine>? = hashMapOf()
        hideViews()
        showLoader()
        if (dataService != null && accessToken != null) {
            doAsync {
                try {
                    Log.i(TAG, "fetching planification lines for planification ${planification?.id}")
                    val call = dataService.getPlanificationLines(url, "Bearer $accessToken", arrayListOf(planification?.id!!)).execute()
                    val planifications = call.body()
                    showViews()
                    hideLoader()
                    if (planifications != null && planifications.size > 0) {
                        Log.i(TAG, "planificacion cargada de internet $planifications")
                        deliveryMap = planifications[0].deliveryMap // obviamente solicitamos un solo resultado
                        if (deliveryMap != null) {
                            val deliveries = deliveryMap?.values?.map{
                                it.planificationId = planification?.id
                                it
                            }?.toMutableList()
                            Log.i(TAG, "deliveries de planificacion: $deliveries")
                            val deliveryLines = mutableListOf<DeliveryLine>()
                            viewModel.planificationLines.postValue(deliveries)
                            var aux: DeliveryLine? = null
                            for(delivery in deliveries!!){
                                for (deliveryLine in delivery.detail) {
                                    for (j in 0 until deliveryLine.quantity) {
                                        aux = deliveryLine.copy(index = j,
                                            deliveryId = delivery.id,
                                            scannedOrder = j + 1,
                                            planificationId = planification?.id!!)
                                        deliveryLines.add(aux)
                                    }
                                }
                            }
                            viewModel.pendingDeliveryLines.postValue(deliveryLines)
                            db?.deliveryDao()?.insertAll(deliveries)
                            db?.deliveryLineDao()?.insertAll(deliveryLines)
                            doAsync {
                                fetchData(this@CertificateActivity::fetchCertifiedLines)
                            }
                            val foundDeliveryLines = db?.pendingToUploadCertificationDao()?.getAll()
                            if (!foundDeliveryLines.isNullOrEmpty()) {
                                Log.i(TAG, "Hay deliveryLines certificados pendientes por subir: $foundDeliveryLines")
                                val _deliveryLines =
                                    db?.deliveryLineDao()?.getAllByIds(foundDeliveryLines.map {
                                        it.deliveryLineId.toInt()
                                    }.toIntArray())
                                if (!_deliveryLines.isNullOrEmpty()) {
                                    viewModel.certifiedDeliveryLines.postValue(_deliveryLines.toMutableList())
                                    viewModel.pendingDeliveryLines.value?.removeAll(_deliveryLines)
                                } else {
                                    Log.i(TAG, "No hay delivery lines que coincidan con los delivery lines certificados pendientes por subir")
                                }
                            } else {
                                Log.i(TAG, "No hay deliveryLines certificados pendientes por subir")
                            }
                            //parsePlanificationLinesFromDeliveryMap(deliveryMap)
                        }
                    }
                } catch(toe: SocketTimeoutException) {
                    hideLoader()
                    showRetryMessage("Network error fetching user planification lines",
                        this@CertificateActivity::fetchPlanificationLinesReq)
                    showSnackbar("Fetching user planification lines failed")
                    Log.e(TAG, "Network error when querying planification lines endpoint", toe)
                } catch (ioEx: IOException) {
                    hideLoader()
                    showRetryMessage("Network error fetching user planification lines",
                        this@CertificateActivity::fetchPlanificationLinesReq)
                    showSnackbar("Fetching user planification lines failed")
                    Log.e(TAG, "Network error when querying planification lines endpoint", ioEx)
                } catch (jsonEx: JSONException) {
                    hideLoader()
                    showRetryMessage("Error parsing user planification lines",
                        this@CertificateActivity::fetchPlanificationLinesReq)
                    Log.e(TAG, "Failed to parse planification lines response", jsonEx)
                    showSnackbar("Failed to parse planification lines")
                } catch (e: Exception) {
                    hideLoader()
                    showRetryMessage("Fetching user planification lines failed",
                        this@CertificateActivity::fetchPlanificationLinesReq)
                    showSnackbar("Fetching planification lines failed")
                    Log.e(TAG, "Unknown exception: ", e)
                }
            }
        }
    }

    fun fetchPlanificationLinesReq() {
        fetchData(this::fetchPlanificationLines)
    }

    private fun showRetryMessage(message: String, callback: () -> Unit) {
        runOnUiThread{
            messageHomeTv?.text = message
            retryBtn?.setOnClickListener {
                callback.invoke()
            }
            retryLayout?.visibility = View.VISIBLE
        }
    }

    /*private fun parsePlanificationLinesFromDeliveryMap(deliveryMap: java.util.HashMap<String, PlanificationLine>?) {
        if (deliveryMap != null) {
            var aux: DeliveryLine
            val deliveries: MutableList<PlanificationLine> = mutableListOf()
            val deliveryLines: MutableList<DeliveryLine> = mutableListOf()
            try {
                for ((deliveryKey, delivery) in deliveryMap) {
                    try {
                        delivery.planificationId = planification?.id!!
                        db?.deliveryDao()?.insert(delivery)
                        deliveries.add(delivery)
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
                    for (deliveryLine in delivery.detail) {
                        for (i in 0 until deliveryLine.quantity) {
                            aux = deliveryLine.copy(index = i,
                                deliveryId = delivery.id,
                                planificationId = planification?.id!!)
                            db?.deliveryLineDao()?.insert(aux)
                            deliveryLines.add(aux)
                        }
                    }
                }
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
    }*/

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu_planification, menu)
        menu.findItem(R.id.startRoute).isVisible = false
        menu.findItem(R.id.endRoute).isVisible = false
        menu.findItem(R.id.cancelRoute).isVisible = false
        when(planification?.state) {
            "Dispatched" -> {
                menu.findItem(R.id.cancelRoute).isVisible = true
                menu.findItem(R.id.startRoute).isVisible = true
                menu.findItem(R.id.endRoute).isVisible = false
            }
            "OnGoing" -> {
                menu.findItem(R.id.cancelRoute).isVisible = true
                menu.findItem(R.id.startRoute).isVisible = false
                menu.findItem(R.id.endRoute).isVisible = true
            }
            "Cancelled" -> {
                menu.findItem(R.id.cancelRoute).isVisible = false
                menu.findItem(R.id.startRoute).isVisible = true
                menu.findItem(R.id.endRoute).isVisible = false
            }
            "Complete" -> {
                menu.findItem(R.id.cancelRoute).isVisible = false
                menu.findItem(R.id.startRoute).isVisible = false
                menu.findItem(R.id.endRoute).isVisible = false
            }
        }
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
        when(planification?.state) {
            "Dispatched" -> {
                menu?.findItem(R.id.cancelRoute)?.isVisible = true
                menu?.findItem(R.id.startRoute)?.isVisible = true
                menu?.findItem(R.id.endRoute)?.isVisible = false
            }
            "OnGoing" -> {
                menu?.findItem(R.id.cancelRoute)?.isVisible = true
                menu?.findItem(R.id.startRoute)?.isVisible = false
                menu?.findItem(R.id.endRoute)?.isVisible = true
            }
            "Cancelled" -> {
                menu?.findItem(R.id.cancelRoute)?.isVisible = false
                menu?.findItem(R.id.startRoute)?.isVisible = true
                menu?.findItem(R.id.endRoute)?.isVisible = false
            }
            "Complete" -> {
                menu?.findItem(R.id.cancelRoute)?.isVisible = false
                menu?.findItem(R.id.startRoute)?.isVisible = false
                menu?.findItem(R.id.endRoute)?.isVisible = false
            }
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
                runOnUiThread {
                    showAlert("Error", "Sesion Expirada", this::signOut)
                }
            }
            return
        }
        hideViews()
        showLoader()
        showSnackbar("Solicitando finalizacion de ruta...")
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
                    routeStarted = true
                    planification?.state = newState
                    viewModel.planification.postValue(planification)
                    try {
                        db?.planificationDao()?.update(planification)
                        /*val intent = Intent(this@MainActivityNational, OnRouteActivity::class.java)
                        intent.putExtra("planification", planification)
                        startActivity(intent)
                        finish()*/
                    } catch (ex: SQLiteException) {
                        Log.e(TAG,
                            "Error actualizando planificacion en la BD local",
                            ex)
                        showAlert(getString(R.string.database_error), getString(R.string.database_error_saving_planifications))
                    } catch (ex: SQLiteConstraintException) {
                        Log.e(TAG,
                            "Error actualizando planificacion en la BD local",
                            ex)
                        showAlert(getString(R.string.database_error), getString(R.string.database_error_saving_planifications))
                    } catch (ex: Exception) {
                        Log.e(TAG,
                            "Error actualizando planificacion en la BD local",
                            ex)
                        showAlert(getString(R.string.database_error), getString(R.string.database_error_saving_planifications))
                    }
                    Log.i(TAG, "finalize planification load response $response")
                } catch(toe: SocketTimeoutException) {
                    Log.e(TAG, "Network error when finalizing planification load", toe)
                    showAlert(getString(R.string.network_error_title), getString(R.string.network_error))
                } catch (ioEx: IOException) {
                    Log.e(TAG,
                        "Network error when finalizing planification load",
                        ioEx)
                    showAlert(getString(R.string.network_error_title), getString(R.string.network_error))
                } catch (jsonEx: JSONException) {
                    Log.e(TAG, "Failed to parse finalizing planification response", jsonEx)
                    showAlert(getString(R.string.parsing_error_title), getString(R.string.parsing_error))
                }
            }
        }
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

    fun hideLoader() {
        runOnUiThread {
            progressBar3.visibility = View.GONE
            retryLayout?.visibility = View.GONE
        }
    }

    fun showLoader() {
        runOnUiThread{
            progressBar3.visibility = View.VISIBLE
            retryLayout?.visibility = View.GONE
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
}