package com.tautech.cclapp.activities.ui_urban.detail

import android.content.DialogInterface
import android.database.sqlite.*
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.tautech.cclapp.R
import com.tautech.cclapp.activities.PlanificationDetailActivity
import com.tautech.cclapp.activities.PlanificationDetailActivityViewModel
import com.tautech.cclapp.classes.AuthStateManager
import com.tautech.cclapp.classes.CclUtilities
import com.tautech.cclapp.classes.Configuration
import com.tautech.cclapp.database.AppDatabase
import com.tautech.cclapp.interfaces.CclDataService
import com.tautech.cclapp.services.CclClient
import kotlinx.android.synthetic.main.fragment_planification_detail.*
import kotlinx.android.synthetic.main.fragment_planification_detail.view.*
import net.openid.appauth.AuthorizationException
import org.jetbrains.anko.doAsync
import retrofit2.Retrofit
import java.io.IOException
import java.net.SocketTimeoutException

class PlanificationDetailFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener {
    val TAG = "PLANIFICATION_DETAIL_FRAGMENT"
    private val viewModel: PlanificationDetailActivityViewModel by activityViewModels()
    private var retrofitClient: Retrofit? = null
    private var mStateManager: AuthStateManager? = null
    var db: AppDatabase? = null
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_planification_detail, container, false)
        retrofitClient = CclClient.getInstance()
        mStateManager = AuthStateManager.getInstance(requireContext())
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
            showAlert("Error", "La configuracion de sesion ha cambiado. Se cerrara su sesion", this::signOut)
        }
        if (!mStateManager!!.current.isAuthorized) {
            showAlert("Sesion expirada", "Su sesion ha expirado", this::signOut)
        }
        viewModel.deliveries.observe(viewLifecycleOwner, Observer{ deliveries ->
            refreshTotals()
        })
        viewModel.planification.observe(viewLifecycleOwner, Observer{planification ->
            val utilities = CclUtilities.getInstance()
            root.planificationStateChip.text = planification.state
            root.planificationStateChip.chipBackgroundColor = when(planification.state) {
                "Created" -> ContextCompat.getColorStateList(requireContext(), R.color.created_bg)
                "Dispatched" -> ContextCompat.getColorStateList(requireContext(), R.color.dispatched_bg)
                "Cancelled" -> ContextCompat.getColorStateList(requireContext(), R.color.cancelled_bg)
                "Complete" -> ContextCompat.getColorStateList(requireContext(), R.color.completed_bg)
                "OnGoing" -> ContextCompat.getColorStateList(requireContext(), R.color.ongoing_bg)
                else -> ContextCompat.getColorStateList(requireContext(), R.color.created_bg)
            }
            root.dateTv.text = planification.dispatchDate
            root.planificationTypeTv?.text = planification.planificationType
            root.planificationLabelTv?.text = planification.label.let {
                if (it.isNullOrEmpty()) {
                    getString(R.string.no_label_planification)
                } else {
                    it
                }
            }
            /*root.planificationCustomerTv.text = planification.customerName
            root.planificationDriverTv.text = planification.driverName*/
            root.planificationDriverTv?.visibility = View.GONE
            root.planificationCustomerTv?.visibility = View.GONE
            root.planificationVehicleTv?.text = planification.licensePlate
            root.totalWeightChip?.text = utilities.formatQuantity(planification.totalWeight ?: 0.0) + " kg"
            root.totalValueChip?.text = utilities.formatQuantity(planification.totalValue ?: 0.0) + " $"
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
        return ((viewModel.planification.value?.totalDeliveredLines ?: 0) * 100) / (viewModel.planification.value?.totalUnits ?: 1)
    }

    fun getCompletedDeliveriesProgress(): Int {
        return ((viewModel.planification.value?.totalDelivered ?: 0) * 100) / (viewModel.planification.value?.totalDeliveries ?: 1)
    }

    private fun refreshTotals(){
        activity?.runOnUiThread {
            planificationCompletedProgressTv?.text =
                "${getCompletedDeliveryLinesProgress()}% ${getString(R.string.completed)}"
            planificationCompletedProgressBar?.progress = getCompletedDeliveryLinesProgress()
            totalItemsChip?.text =
                "${viewModel.planification.value?.totalDelivered}/${viewModel.planification.value?.totalDeliveries ?: 0}"
            deliveriesCompletedProgressTv?.text =
                "${getCompletedDeliveriesProgress()}% ${getString(R.string.completed)}"
            deliveriesCompletedProgressBar?.progress = getCompletedDeliveriesProgress()
            deliveryLinesCountChip.text =
                "${viewModel.planification.value?.totalDeliveredLines}/${viewModel.planification.value?.totalUnits ?: 0}"
            deliveryLinesCompletedProgressTv?.text =
                "${getCompletedDeliveryLinesProgress()}% ${getString(R.string.completed)}"
            deliveryLinesCompletedProgressBar?.progress = getCompletedDeliveryLinesProgress()
        }
    }

    override fun onRefresh() {
        loadStateFormDefinitions()
    }

    fun loadStateFormDefinitions() {
        fetchData(this::loadStateFormDefinitions)
    }

    private fun loadStateFormDefinitions(accessToken: String?, idToken: String?, ex: AuthorizationException?) {
        if (ex != null) {
            Log.e(TAG, "ocurrio una excepcion mientras se recuperaban las definicones de formularios", ex)
            if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {
                showAlert("Sesion expirada", "Su sesion ha expirado", this::signOut)
            }
            return
        }
        val url = "api/customers/stateConfing?customer-id=${viewModel.planification.value?.customerId}"
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
                            Log.i(TAG, "guardando en DB local definiciones")
                            if(viewModel.planification.value?.customerId != null) {
                                db?.stateFormDefinitionDao()
                                    ?.deleteAllByCustomer(viewModel.planification.value?.customerId!!)
                            }
                            db?.stateFormDefinitionDao()?.insertAll(response)
                            response.flatMap{def ->
                                def.formFieldList?.forEach {field ->
                                    field.formDefinitionId = def.id
                                }
                                def.formFieldList ?: listOf()
                            }.also{fields ->
                                db?.stateFormFieldDao()?.deleteAll()
                                if (fields.isNotEmpty()) {
                                    db?.stateFormFieldDao()?.insertAll(fields)
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
        mStateManager?.current?.performActionWithFreshTokens(mStateManager?.mAuthService!!, callback)
    }

    private fun signOut() {
        mStateManager?.signOut(requireContext())
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