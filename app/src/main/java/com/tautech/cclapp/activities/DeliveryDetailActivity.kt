package com.tautech.cclapp.activities

import android.content.DialogInterface
import android.content.Intent
import android.database.sqlite.*
import android.os.Bundle
import android.util.Log
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
import com.google.gson.Gson
import com.tautech.cclapp.R
import com.tautech.cclapp.classes.AuthStateManager
import com.tautech.cclapp.classes.Configuration
import com.tautech.cclapp.database.AppDatabase
import com.tautech.cclapp.interfaces.CclDataService
import com.tautech.cclapp.models.Delivery
import com.tautech.cclapp.models.DeliveryLine
import com.tautech.cclapp.models.Planification
import com.tautech.cclapp.services.CclClient
import kotlinx.android.synthetic.main.activity_delivery_detail.*
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.EndSessionResponse
import org.jetbrains.anko.contentView
import org.jetbrains.anko.doAsync
import retrofit2.Retrofit
import java.io.IOException
import java.net.SocketTimeoutException

class DeliveryDetailActivity : AppCompatActivity() {
    private val MANAGE_ACTIVITY = 1
    private var newState: String = ""
    val TAG = "DELIVERY_DETAIL_ACTIVITY"
    var planification: Planification? = null
    var delivery: Delivery? = null
    private var retrofitClient: Retrofit? = null
    private var mStateManager: AuthStateManager? = null
    var db: AppDatabase? = null
    private val viewModel: DeliveryDetailActivityViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_delivery_detail)
        val navView: BottomNavigationView = findViewById(R.id.nav_view3)
        val navController = findNavController(R.id.nav_host_fragment_delivery_detail)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(setOf(
            R.id.navigation_delivery_detail, R.id.navigation_delivery_items))
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        manageBtn.setOnClickListener {
            showStateChoosingAlert()
        }
        manageBtn.visibility = View.GONE
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
            showAlert("Error", "Sesion Expirada", this::signOut)
            return
        }
        viewModel.planification.observe(this, Observer{_planification ->
            Log.i(TAG, "Planification observada: ${_planification.id}")
            if (_planification != null) {
                planification = _planification
                invalidateOptionsMenu()
            }
        })
        viewModel.delivery.observe(this, Observer{_del ->
            Log.i(TAG, "Delivery observada: ${_del.deliveryId}")
            if (_del != null) {
                delivery = _del
                if(viewModel.deliveryLines.value.isNullOrEmpty()) {
                    getDeliveryLines()
                }
                invalidateOptionsMenu()
                checkManageBtnStatus()
            }
        })
        val extras = intent.extras
        if (extras != null) {
            // TODO obtener planificacion id de shared preferences y luego la planificacion de la BD
            if (extras.containsKey("planification")) {
                planification = extras.getSerializable("planification") as Planification
                Log.i(TAG, "planificacion recibida en intent: $planification")
            } else {
                Log.i(TAG, "no se recibio ninguna planificacion en el intent")
                finish()
            }
            if (extras.containsKey("delivery")) {
                delivery = extras.getSerializable("delivery") as Delivery
                Log.i(TAG, "delivery recibida en intent: $delivery")
            } else {
                Log.i(TAG, "no se recibio ninguna delivery en el intent")
                finish()
            }
        } else {
            Log.i(TAG, "no se recibieron datos")
            finish()
        }
    }

    private fun getDeliveryLines() {
        doAsync {
            //if(viewModel.deliveryLines.value?.isNullOrEmpty() == true){
                Log.i(TAG, "delivery lines es nulo o vacio, solicitando delivery lines remotos...")
                fetchData(this@DeliveryDetailActivity::fetchDeliveryLines)
            /*} else {
                Log.i(TAG, "delivery lines no es nulo o vacio, obteniendo delivery lines locales...")
                val localDeliveryLines = db?.deliveryDao()?.getGroupedLines(delivery?.deliveryId!!)
                if (!localDeliveryLines.isNullOrEmpty()) {
                    viewModel.deliveryLines.postValue(localDeliveryLines.toMutableList())
                }
            }*/
        }
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume()...")
        showLoader()
        mStateManager?.revalidateSessionData(this)
        doAsync {
            Log.i(TAG, "buscando planificacion ${planification?.id} en BD local...")
            val planification = db?.planificationDao()?.getById(planification?.id!!)
            Log.i(TAG, "planification loaded from local DB: $planification")
            Log.i(TAG, "buscando delivery ${delivery?.deliveryId} en BD local...")
            val delivery = db?.deliveryDao()?.getById(delivery?.deliveryId)
            Log.i(TAG, "delivery loaded from local DB: $delivery")
            if (delivery != null) {
                //delivery.detail.addAll(db?.deliveryDao()?.getGroupedLines(delivery.id) ?: listOf())
                viewModel.delivery.postValue(delivery)
            }
            hideLoader()
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

    fun checkManageBtnStatus(){
        if (listOf("OnGoing", "Planned", "DeliveryPlanned", "Created", "UnDelivered").contains(delivery?.deliveryState)) {
            manageBtn.visibility = View.VISIBLE
        } else {
            manageBtn.visibility = View.GONE
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
                    val mainIntent = Intent(this,
                        LoginActivity::class.java)
                    mainIntent.flags =
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(mainIntent)
                    finishAndRemoveTask()
                }
            } else {
                Log.e(TAG, "Error al intentar finalizar sesion", ex)
                showAlert("Error",
                    "No se pudo finalizar la sesion remota",
                    this::signOut)
            }
        }
        if (requestCode == MANAGE_ACTIVITY && data?.hasExtra("deliveredLines") == true){
            Log.i(TAG, "volviendo de manage activity...")
            val deliveryLines = data.getSerializableExtra("deliveredLines") as ArrayList<DeliveryLine>
            Log.i(TAG, "delivery lines obtenidas de activity manage: $deliveryLines")
            if (!deliveryLines.isNullOrEmpty()) {
                Log.i(TAG, "actualizando delivery lines...")
                viewModel.deliveryLines.value?.forEach {it ->
                    val found = deliveryLines.find{dl ->
                        dl.id == it.id
                    }
                    it.delivered = found?.delivered ?: 0
                }
                viewModel.deliveryLines.postValue(viewModel.deliveryLines.value)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    fun askForChangeState(state: String) {
        newState = state
        when(state) {
            "Cancelled" -> {
                showAlert(getString(R.string.cancel_delivery), getString(R.string.cancel_delivery_prompt), this::changeDeliveryState)
            }
            "Delivered" -> {
                showAlert(getString(R.string.deliver_delivery), getString(R.string.deliver_delivery_prompt), this::changeDeliveryState)
            }
            "UnDelivered" -> {
                showAlert(getString(R.string.undeliver_delivery), getString(R.string.undeliver_delivery_prompt), this::changeDeliveryState)
            }
        }
    }

    fun changeDeliveryState() {
        fetchData(this::changeDeliveryState)
    }

    private fun changeDeliveryState(accessToken: String?, idToken: String?, ex: AuthorizationException?) {
        if (ex != null) {
            Log.e(TAG, "ocurrio una excepcion al intentar cambiar estado", ex)
            showAlert("Error", "Sesion Expirada", this::signOut)
            return
        }
        showLoader()
        val dataService: CclDataService? = CclClient.getInstance()?.create(
            CclDataService::class.java)
        if (dataService != null && accessToken != null && delivery != null) {
            val urlChangeState = "delivery/${delivery?.deliveryId}/changeState?newState=$newState"
            doAsync {
                showSnackbar("Solicitando cambio de estado del delivery...")
                try {
                    val callChangeState = dataService.changeDeliveryState(urlChangeState,
                        "Bearer $accessToken")
                        .execute()
                    hideLoader()
                    Log.i(TAG, "finalize planification load response ${callChangeState.code()}")
                    if (callChangeState.code() == 200) {
                        delivery?.deliveryState = newState
                        viewModel.delivery.postValue(delivery)
                        try {
                            db?.deliveryDao()?.update(delivery!!)
                        } catch (ex: SQLiteException) {
                            Log.e(TAG,
                                "Error actualizando delivery en la BD local",
                                ex)
                            showAlert(getString(R.string.database_error), getString(R.string.database_error_saving_planifications))
                        } catch (ex: SQLiteConstraintException) {
                            Log.e(TAG,
                                "Error actualizando delivery en la BD local",
                                ex)
                            showAlert(getString(R.string.database_error), getString(R.string.database_error_saving_planifications))
                        } catch (ex: Exception) {
                            Log.e(TAG,
                                "Error actualizando delivery en la BD local",
                                ex)
                            showAlert(getString(R.string.database_error), getString(R.string.database_error_saving_planifications))
                        }
                    }
                } catch(toe: SocketTimeoutException) {
                    Log.e(TAG, "Network error when changing delivery state", toe)
                    showAlert(getString(R.string.network_error_title), getString(R.string.network_error))
                } catch (ioEx: IOException) {
                    Log.e(TAG,
                        "Network error when changing delivery state",
                        ioEx)
                    showAlert(getString(R.string.network_error_title), getString(R.string.network_error))
                }
            }
        }
    }

    private fun fetchDeliveryLines(accessToken: String?, idToken: String?, ex: AuthorizationException?) {
        if (ex != null) {
            Log.e(TAG, "ocurrio una excepcion al intentar obtener delivery lines", ex)
            showAlert("Error", "Sesion Expirada", this::signOut)
            return
        }
        showLoader()
        val dataService: CclDataService? = CclClient.getInstance()?.create(
            CclDataService::class.java)
        if (dataService != null && accessToken != null && delivery != null) {
            val url = "planificationDeliveryDetailVO1s/search/findByDeliveryId?deliveryId=${delivery?.deliveryId}"
            doAsync {
                try {
                    val call = dataService.getDeliveryDeliveryLines(url,
                        "Bearer $accessToken")
                        .execute()
                    hideLoader()
                    val response = call.body()
                    Log.i(TAG, "fetching delivery lines response $response")
                    if (response != null && response._embedded.planificationDeliveryDetailVO1s.isNotEmpty()) {
                        for (deliveryLine in response._embedded.planificationDeliveryDetailVO1s) {
                            deliveryLine.planificationId = planification?.id!!
                        }
                        viewModel.deliveryLines.postValue(response._embedded.planificationDeliveryDetailVO1s)
                        try {
                            db?.deliveryLineDao()?.deleteAllByDelivery(viewModel.delivery.value?.deliveryId!!)
                            db?.deliveryLineDao()?.insertAll(response._embedded.planificationDeliveryDetailVO1s)
                        } catch (ex: SQLiteException) {
                            Log.e(TAG,
                                "Error actualizando delivery lines en la BD local",
                                ex)
                            showAlert(getString(R.string.database_error), getString(R.string.database_error_saving_delivery_lines))
                        } catch (ex: SQLiteConstraintException) {
                            Log.e(TAG,
                                "Error actualizando delivery lines en la BD local",
                                ex)
                            showAlert(getString(R.string.database_error), getString(R.string.database_error_saving_delivery_lines))
                        } catch (ex: Exception) {
                            Log.e(TAG,
                                "Error actualizando delivery lines en la BD local",
                                ex)
                            showAlert(getString(R.string.database_error), getString(R.string.database_error_saving_delivery_lines))
                        }
                    }
                } catch(toe: SocketTimeoutException) {
                    Log.e(TAG, "Network error when fetching delivery lines", toe)
                    showAlert(getString(R.string.network_error_title), getString(R.string.network_error))
                } catch (ioEx: IOException) {
                    Log.e(TAG,
                        "Network error when fetching delivery lines",
                        ioEx)
                    showAlert(getString(R.string.network_error_title), getString(R.string.network_error))
                }
            }
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

    fun showStateChoosingAlert() {
        runOnUiThread {
            val builder = AlertDialog.Builder(this)
            builder.setTitle(getString(R.string.choose_state))
            builder.setItems(when(delivery?.deliveryState){
                "UnDelivered" -> R.array.delivery_states_actions_undelivered
                else -> R.array.delivery_states_actions}) { dialog, which ->
                when(which){
                    0 -> {
                        val intent = Intent(this, ManageDeliveryActivity::class.java)
                        intent.putExtra("planification", planification)
                        intent.putExtra("delivery", delivery)
                        intent.putExtra("state", "Delivered")
                        startActivityForResult(intent, MANAGE_ACTIVITY)
                    }
                    1 -> {
                        //askForChangeState("UnDelivered")
                        val intent = Intent(this, ManageDeliveryActivity::class.java)
                        intent.putExtra("planification", planification)
                        intent.putExtra("delivery", delivery)
                        intent.putExtra("state", "UnDelivered")
                        startActivityForResult(intent, MANAGE_ACTIVITY)
                    }
                }
            }
            val dialog: AlertDialog = builder.create()
            dialog.show()
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
            Snackbar.make(contentView!!,
                message,
                Snackbar.LENGTH_SHORT)
                .show()
        }
    }
}