package com.tautech.cclapp.activities.ui_urban.detail

import android.content.DialogInterface
import android.content.Intent
import android.database.sqlite.*
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getColor
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.tautech.cclapp.*
import com.tautech.cclapp.activities.LoginActivity
import com.tautech.cclapp.activities.PlanificationDetailActivity
import com.tautech.cclapp.activities.PlanificationDetailActivityViewModel
import com.tautech.cclapp.classes.AuthStateManager
import com.tautech.cclapp.classes.Configuration
import com.tautech.cclapp.database.AppDatabase
import com.tautech.cclapp.interfaces.CclDataService
import com.tautech.cclapp.models.PlanificationLine
import com.tautech.cclapp.models.StateFormDefinition
import com.tautech.cclapp.services.CclClient
import kotlinx.android.synthetic.main.fragment_planification_detail.*
import kotlinx.android.synthetic.main.fragment_planification_detail.view.*
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import org.jetbrains.anko.doAsync
import retrofit2.Retrofit
import java.io.IOException
import java.net.SocketTimeoutException

class PlanificationDetailFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener {
    val TAG = "PLANIFICATION_DETAIL_FRAGMENT"
    private val viewModel: PlanificationDetailActivityViewModel by activityViewModels()
    private var retrofitClient: Retrofit? = null
    private var mAuthService: AuthorizationService? = null
    private var mStateManager: AuthStateManager? = null
    private var mConfiguration: Configuration? = null
    var db: AppDatabase? = null
    var totalDeliveriesDelivered = 0
    var totalDeliveryLinesDelivered = 0
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_planification_detail, container, false)
        var deliveredDeliveries: List<PlanificationLine> = listOf()
        retrofitClient = CclClient.getInstance()
        mStateManager = AuthStateManager.getInstance(requireContext())
        mConfiguration = Configuration.getInstance(requireContext())
        val config = Configuration.getInstance(requireContext())
        try {
            db = AppDatabase.getDatabase(requireContext())
        } catch(ex: SQLiteDatabaseLockedException) {
            Log.e(TAG, "Database error found", ex)
        } catch (ex: SQLiteAccessPermException) {
            Log.e(TAG, "Database error found", ex)
        } catch (ex: SQLiteCantOpenDatabaseException) {
            Log.e(TAG, "Database error found", ex)
        }
        if (config.hasConfigurationChanged()) {
            Toast.makeText(
                requireContext(),
                "Configuration change detected",
                Toast.LENGTH_SHORT)
                .show()
            signOut()
        }
        mAuthService = AuthorizationService(
            requireContext(),
            AppAuthConfiguration.Builder()
                .setConnectionBuilder(config.connectionBuilder)
                .build())
        if (!mStateManager!!.current.isAuthorized) {
            Log.i(TAG, "No hay autorizacion para el usuario")
            signOut()
        }
        viewModel.deliveries.observe(viewLifecycleOwner, Observer{ deliveries ->
            deliveredDeliveries = deliveries.filter {
                it.deliveryState == "Delivered"
            }
            calculateTotals(deliveredDeliveries)
        })
        viewModel.planification.observe(viewLifecycleOwner, Observer{planification ->
            root.planificationStateChip.text = planification.state
            root.planificationStateChip.chipBackgroundColor = when(planification.state) {
                "Created" -> ContextCompat.getColorStateList(requireContext(), R.color.created_bg)
                "Dispatched" -> ContextCompat.getColorStateList(requireContext(), R.color.dispatched_bg)
                "Cancelled" -> ContextCompat.getColorStateList(requireContext(), R.color.cancelled_bg)
                "Complete" -> ContextCompat.getColorStateList(requireContext(), R.color.completed_bg)
                "OnGoing" -> ContextCompat.getColorStateList(requireContext(), R.color.ongoing_bg)
                else -> ContextCompat.getColorStateList(requireContext(), R.color.created_bg)
            }
            root.planificationTypeTv.text = planification.planificationType
            root.planificationLabelTv.text = planification.label.let {
                if (it.isNullOrEmpty()) {
                    getString(R.string.no_label_planification)
                } else {
                    it
                }
            }
            root.planificationCustomerTv.text = planification.customerName
            root.planificationDriverTv.text = planification.driverName
            root.planificationVehicleTv.text = planification.vehicleLicensePlate
            root.totalWeightChip.text = "%.2f".format(planification.totalWeight ?: 0) + " kg"
            root.totalValueChip.text = "%.2f".format(planification.totalValue ?: 0) + " $"
            refreshTotals()
        })
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as PlanificationDetailActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
        (activity as PlanificationDetailActivity).supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    fun getCompletedDeliveryLinesProgress(): Int {
        return (totalDeliveryLinesDelivered * 100) / (viewModel.planification.value?.totalUnits ?: 1)
    }

    fun getCompletedDeliveriesProgress(): Int {
        return (totalDeliveriesDelivered * 100) / (viewModel.planification.value?.totalDeliveries ?: 1)
    }

    fun calculateTotals(deliveredDeliveries: List<PlanificationLine> ){
        totalDeliveriesDelivered = deliveredDeliveries.size
        totalDeliveryLinesDelivered = 0
        deliveredDeliveries.forEach {
            if (it.deliveryState == "Delivered"){
                totalDeliveryLinesDelivered += it.totalQuantity ?: 0
            }
        }
        refreshTotals()
    }

    fun refreshTotals(){
        planificationCompletedProgressTv.text = "${getCompletedDeliveryLinesProgress()}% ${getString(R.string.completed)}"
        planificationCompletedProgressBar.progress = getCompletedDeliveryLinesProgress()
        totalItemsChip.text = "${totalDeliveriesDelivered ?: 0}/${viewModel.planification.value?.totalDeliveries ?: 0}"
        deliveriesCompletedProgressTv.text = "${getCompletedDeliveriesProgress()}% ${getString(R.string.completed)}"
        deliveriesCompletedProgressBar.progress = getCompletedDeliveriesProgress()
        deliveryLinesCountChip.text = "${totalDeliveryLinesDelivered ?: 0}/${viewModel.planification.value?.totalUnits ?: 0}"
        deliveryLinesCompletedProgressTv.text = "${getCompletedDeliveryLinesProgress()}% ${getString(R.string.completed)}"
        deliveryLinesCompletedProgressBar.progress = getCompletedDeliveryLinesProgress()
    }

    override fun onRefresh() {
        loadStateFormDefinitions()
    }

    fun loadStateFormDefinitions() {
        fetchData(this::loadStateFormDefinitions)
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
        //val url = "stateFormDefinitions/search/findByCustomerId?customerId==${viewModel.planification.value?.customerId}"
        val url = "api/customers/stateConfing?customer-id=${viewModel.planification.value?.customerId}"
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

    private fun fetchData(callback: ((String?, String?, AuthorizationException?) -> Unit)) {
        Log.i(TAG, "Fetching data...$callback")
        try {
            mStateManager?.current?.performActionWithFreshTokens(mAuthService!!,
                callback)
        }catch (ex: AuthorizationException) {
            Log.e(TAG, "error fetching data", ex)
        }
    }

    private fun signOut() {
        mStateManager?.signOut(requireContext())
        val mainIntent = Intent(requireContext(), LoginActivity::class.java)
        mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK and Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(mainIntent)
        activity?.finish()
    }

    fun showAlert(title: String, message: String) {
        activity?.runOnUiThread {
            val builder = AlertDialog.Builder(requireContext())
            builder.setTitle(title)
            builder.setMessage(message)
            builder.setPositiveButton("Aceptar", null)
            val dialog: AlertDialog = builder.create();
            dialog.show();
        }
    }

    fun showAlert(title: String, message: String, positiveCallback: (() -> Unit)? = null, negativeCallback: (() -> Unit)? = null) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("Aceptar", DialogInterface.OnClickListener{ dialog, id ->
            if (positiveCallback != null) {
                positiveCallback()
            }
            dialog.dismiss()
        })
        builder.setNegativeButton("Cancelar", DialogInterface.OnClickListener{ dialog, id ->
            if (negativeCallback != null) {
                negativeCallback()
            }
            dialog.dismiss()
        })
        if(this.activity?.isDestroyed == false && this.activity?.isFinishing == false) {
            val dialog: AlertDialog = builder.create()
            dialog.show()
        }
    }
}