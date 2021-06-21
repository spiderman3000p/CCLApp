package com.tautech.cclapp.activities.legalization

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat.getColor
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.gson.Gson
import com.tautech.cclapp.R
import com.tautech.cclapp.activities.PlanificationDetailActivityViewModel
import com.tautech.cclapp.classes.AuthStateManager
import com.tautech.cclapp.classes.CclUtilities
import com.tautech.cclapp.classes.Configuration
import com.tautech.cclapp.interfaces.CclDataService
import com.tautech.cclapp.models.*
import com.tautech.cclapp.services.CclClient
import com.tautech.cclapp.services.MyWorkerManagerService
import kotlinx.android.synthetic.main.fragment_legalize_payments.*
import net.openid.appauth.AuthorizationException
import org.jetbrains.anko.doAsync
import org.json.JSONException
import java.io.IOException
import java.net.SocketTimeoutException


class LegalizePaymentsDialog(val planificationId: Long, val mFunction: () -> Unit) : DialogFragment(),
    OnChartValueSelectedListener {
    val TAG = "LEGALIZE_PAYMENTS_DIALOG"
    private var mStateManager: AuthStateManager? = null
    private lateinit var viewModel: PlanificationDetailActivityViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NORMAL, R.style.AppTheme_FullScreenDialog)
        mStateManager = AuthStateManager.getInstance(requireContext())
        val config = Configuration.getInstance(requireContext())
        if (config.hasConfigurationChanged()) {
            Log.e(TAG, "La configuracion de sesion ha cambiado. Se cerrara su sesion")
            return
        }
        if (!mStateManager!!.current.isAuthorized) {
            Log.e(TAG, "Sesion expirada. Su sesion ha expirado")
            return
        }
        val _viewModel: PlanificationDetailActivityViewModel by activityViewModels()
        viewModel = _viewModel
    }

    override fun onStart() {
        super.onStart()
        val dialog: Dialog? = dialog
        if (dialog != null) {
            val width = ViewGroup.LayoutParams.MATCH_PARENT
            val height = ViewGroup.LayoutParams.MATCH_PARENT
            dialog.getWindow()?.setLayout(width, height)
            dialog.getWindow()?.setWindowAnimations(R.style.AppThemeSlide);
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val view: View = inflater.inflate(R.layout.fragment_legalize_payments, container, false)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.setNavigationOnClickListener { v: View? -> dismiss() }
        toolbar.title = getString(R.string.legalize_payments)
        toolbar.setTitleTextColor(getColor(requireContext(), R.color.white))
        getPlanificationDetails()
        val totalPayments = (viewModel.cashPayments.value?.fold(0.0, { sum, _payment ->
            sum + (_payment.detail?.amount ?: 0.0)
        }) ?: 0.0)
        val toValidate = viewModel.paymentDetails.value?.cash?.value ?: 0.0
        Log.i(TAG, "totalPayments: $totalPayments")
        Log.i(TAG, "toValidate: $toValidate")
        doneBtn.isEnabled = toValidate > 0 && totalPayments >= toValidate
        Log.i(TAG, "doneBtn.isEnabled: ${toValidate > 0 && totalPayments >= toValidate}")
        viewModel.paymentDetails.observe(viewLifecycleOwner, {
            activity?.runOnUiThread {
                // cash data
                totalCashPaymentsCountTv.text = "(${viewModel.cashPayments.value?.size ?: 0})"
                totalCashDeliveriesTv.text =
                    (viewModel.paymentDetails.value?.cash?.count ?: 0).toString()
                totalCashReceivedTv.text = CclUtilities.getInstance()
                    .formatCurrencyNumber(viewModel.paymentDetails.value?.cash?.value ?: 0.0)
                val _totalPayments = (viewModel.cashPayments.value?.fold(0.0, { sum, _payment ->
                    sum + (_payment.detail?.amount ?: 0.0)
                }) ?: 0.0)
                val _toValidate = viewModel.paymentDetails.value?.cash?.value ?: 0.0
                val validationReady = _toValidate > 0 && _totalPayments >= _toValidate
                doneBtn.isEnabled = validationReady
                Log.i(TAG, "doneBtn.isEnabled: $validationReady")
                totalCashLegalizedTv.text =
                    CclUtilities.getInstance().formatCurrencyNumber(_totalPayments)
                // voucher data
                totalVouchersDeliveriesTv.text =
                    (viewModel.paymentDetails.value?.electronic?.count ?: 0).toString()
                totalVouchersReceivedTv.text = CclUtilities.getInstance()
                    .formatCurrencyNumber(viewModel.paymentDetails.value?.electronic?.value ?: 0.0)
                // credit data
                totalCreditDeliveriesTv.text =
                    (viewModel.paymentDetails.value?.credit?.count ?: 0).toString()
                totalCreditReceivedTv.text = CclUtilities.getInstance()
                    .formatCurrencyNumber(viewModel.paymentDetails.value?.credit?.value ?: 0.0)
                // returns data
                totalReturnsDeliveriesTv.text =
                    (viewModel.paymentDetails.value?.returns?.count ?: 0).toString()
                totalReturnsReceivedTv.text = CclUtilities.getInstance()
                    .formatCurrencyNumber(viewModel.paymentDetails.value?.returns?.value ?: 0.0)
            }
        })
        viewModel.cashPayments.observe(viewLifecycleOwner, { _payments ->
            Log.i(TAG, "pagos observados: $_payments")
            val utilities = CclUtilities.getInstance()
            val _totalPayments = (viewModel.cashPayments.value?.fold(0.0, { sum, _payment ->
                sum + (_payment.detail?.amount ?: 0.0)
            }) ?: 0.0)
            val _toValidate = viewModel.paymentDetails.value?.cash?.value ?: 0.0
            val validationReady = _toValidate > 0 && _totalPayments >= _toValidate
            Log.i(TAG, "doneBtn.isEnabled: $validationReady")
            activity?.runOnUiThread {
                doneBtn.isEnabled = validationReady
                totalCashLegalizedTv.text = utilities.formatCurrencyNumber(_totalPayments)
                totalCashPaymentsCountTv.text = "(${_payments.size ?: 0})"
            }
        })
        editCashPaymentsBtn.setOnClickListener {
            PaymentsValidationListDialog.display(parentFragmentManager, planificationId)
        }
        doneBtn.setOnClickListener {
            CclUtilities.getInstance().showAlert(requireActivity(), getString(R.string.confirmation), getString(R.string.legalize_planification_confirm_msg), this::legalize)
        }
    }

    private fun fetchData(callback: ((String?, String?, AuthorizationException?) -> Unit)) {
        Log.i(TAG, "fetching data...")
        mStateManager?.current?.performActionWithFreshTokens(mStateManager?.mAuthService!!,
            callback)
    }

    private fun getPlanificationDetails(){
        fetchData(this::getPlanificationDetails)
    }

    private fun getPlanificationDetails(
        accessToken: String?,
        idToken: String?,
        ex: AuthorizationException?,
    ) {
        if (ex != null) {
            Log.e(TAG, "ocurrio una excepcion al cambiar estado de planificacion", ex)
            if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {
                Log.e(TAG, "Sesion expirada. Su sesion ha expirado")
            }
            return
        }
        showLoader()
        val url = "collectionVO1s/search/findByPlanificationId?planificationId=$planificationId"
        val dataService: CclDataService? = CclClient.getInstance()?.create(
            CclDataService::class.java)
        if (dataService != null && accessToken != null) {
            doAsync {
                try {
                    val call = dataService.getPlanificationPaymentDetails(url,
                        "Bearer $accessToken")
                        .execute()
                    hideLoader()
                    call.body()?.let {
                        val gson = Gson()
                        val cashStr = it.cash?.replace("\\","")
                        val creditStr = it.credit?.replace("\\","")
                        val electronicStr = it.electronic?.replace("\\","")
                        val returnsStr = it.returns?.replace("\\","")
                        Log.i(TAG, "cash json: $cashStr")
                        Log.i(TAG, "credit json: $creditStr")
                        Log.i(TAG, "electronic json: $electronicStr")
                        Log.i(TAG, "returns json: $returnsStr")
                        val planificationPaymentDetails = PlanificationPaymentDetails(
                            cash = gson.fromJson(cashStr, PaymentDetail::class.java),
                            credit = gson.fromJson(creditStr, PaymentDetail::class.java),
                            electronic = gson.fromJson(electronicStr, PaymentDetail::class.java),
                            returns = gson.fromJson(returnsStr, PaymentDetail::class.java)
                        )
                        Log.i(TAG, "informacion de pagos recibida: $planificationPaymentDetails")
                        viewModel.paymentDetails.postValue(planificationPaymentDetails)
                    }
                } catch (toe: SocketTimeoutException) {
                    Log.e(TAG, "Network error when fetching planification details", toe)
                    CclUtilities.getInstance().showAlert(requireActivity(), getString(R.string.network_error_title),
                        getString(R.string.network_error))
                } catch (ioEx: IOException) {
                    Log.e(TAG,
                        "Network error when fetching planification details",
                        ioEx)
                    CclUtilities.getInstance().showAlert(requireActivity(), getString(R.string.network_error_title),
                        getString(R.string.network_error))
                } catch (jsonEx: JSONException) {
                    Log.e(TAG, "Failed to parse finalizing planification response", jsonEx)
                    CclUtilities.getInstance().showAlert(requireActivity(),getString(R.string.parsing_error_title),
                        getString(R.string.parsing_error))
                } finally {
                    hideLoader()
                }
            }
        }
    }

    fun legalize(){
        MyWorkerManagerService.enqueUploadSingleLegalizationWork(requireContext(), viewModel.cashPayments.value?.toList()!!, planificationId)
        viewModel.legalizedComplete.postValue(true)
        dismiss()
    }

    private fun legalize(
        accessToken: String?,
        idToken: String?,
        ex: AuthorizationException?,
    ) {
        if (ex != null) {
            Log.e(TAG, "ocurrio una excepcion al intentar legalizar la planificacion", ex)
            if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {
                Log.e(TAG, "Sesion expirada. Su sesion ha expirado")
            }
            return
        }
        showLoader()
        try {
            val totalPayments = (viewModel.cashPayments.value?.fold(0.0, { sum, _payment ->
                sum + (_payment.detail?.amount ?: 0.0)
            }) ?: 0.0)
            val detailsMap = viewModel.cashPayments.value?.map {
                it.detail!!
            }
            val paymentLegalization = PaymentLegalization(
                planificationId = planificationId,
                amount = totalPayments,
                detail = detailsMap
            )

            val url = "planification/${planificationId}/legalize"
            val dataService: CclDataService? = CclClient.getInstance()?.create(
                CclDataService::class.java)
            if (dataService != null && accessToken != null) {
                doAsync {
                    try {
                        val call = dataService.legalizePlanificationPayments(url,
                            paymentLegalization,
                            "Bearer $accessToken")
                            .execute()
                        hideLoader()
                        if (call.code() == 200 || call.code() == 201){
                            mFunction()
                            Toast.makeText(requireContext(), getString(R.string.legalization_done_successfully), Toast.LENGTH_LONG).show()
                            //MyWorkerManagerService.enquePaymentPhotoUpload()
                        } else {
                            CclUtilities.getInstance().showAlert(requireActivity(),getString(R.string.error),
                                getString(R.string.error_sending_legalization_msg))
                        }
                    } catch (toe: SocketTimeoutException) {
                        Log.e(TAG, "Network error when sending legalization", toe)
                        CclUtilities.getInstance().showAlert(requireActivity(),getString(R.string.network_error_title),
                            getString(R.string.network_error))
                    } catch (ioEx: IOException) {
                        Log.e(TAG,
                            "Network error when sending legalization",
                            ioEx)
                        CclUtilities.getInstance().showAlert(requireActivity(),getString(R.string.network_error_title),
                            getString(R.string.network_error))
                    } catch (jsonEx: JSONException) {
                        Log.e(TAG, "Failed to parse finalizing legalization response", jsonEx)
                        CclUtilities.getInstance().showAlert(requireActivity(),getString(R.string.parsing_error_title),
                            getString(R.string.parsing_error))
                    } finally {
                        hideLoader()
                    }
                }
            }
        }catch (e: Exception){
            FirebaseCrashlytics.getInstance().recordException(e)
            e.printStackTrace()
            Log.e(TAG, "Error al enviar legalizacion")
            CclUtilities.getInstance().showAlert(requireActivity(),getString(R.string.error), getString(R.string.error_sending_legalization_msg))
        }
    }

    fun hideLoader() {
        activity?.runOnUiThread {
            progressBar?.visibility = View.GONE
        }
    }

    fun showLoader() {
        activity?.runOnUiThread{
            progressBar?.visibility = View.VISIBLE
        }
    }

    companion object {
        fun display(fragmentManager: FragmentManager, planificationId: Long, mFunction: () -> Unit): LegalizePaymentsDialog? {
            val previewDialog = LegalizePaymentsDialog(planificationId, mFunction)
            previewDialog.show(fragmentManager, "legalize_payments_dialog")
            return previewDialog
        }
    }

    override fun onValueSelected(e: Entry?, h: Highlight?) {

    }

    override fun onNothingSelected() {

    }
}