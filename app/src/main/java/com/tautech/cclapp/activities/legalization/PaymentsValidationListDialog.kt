package com.tautech.cclapp.activities.legalization

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat.getColor
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.tautech.cclapp.R
import com.tautech.cclapp.activities.PlanificationDetailActivityViewModel
import com.tautech.cclapp.adapters.PaymentAdapter
import com.tautech.cclapp.classes.CclUtilities
import com.tautech.cclapp.models.Payment
import kotlinx.android.synthetic.main.fragment_delivery_payment_list_screen.*

class PaymentsValidationListDialog(val planificationId: Long) : DialogFragment(),
    OnChartValueSelectedListener {
    val TAG = "PAYMENT_VALIDATION_LIST_DIALOG"
    private var mAdapter: PaymentAdapter? = null
    private lateinit var viewModel: PlanificationDetailActivityViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NORMAL, R.style.AppTheme_FullScreenDialog)
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
        val view: View = inflater.inflate(R.layout.fragment_delivery_payment_list_screen, container, false)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.setNavigationOnClickListener { v: View? -> dismiss() }
        toolbar.title = getString(R.string.cash_payment_validation_list)
        toolbar.setTitleTextColor(getColor(requireContext(), R.color.white))
        doneBtn.text = getString(R.string.done)
        val utilities = CclUtilities.getInstance()
        viewModel.cashPayments.observe(viewLifecycleOwner, Observer { _payments ->
            Log.i(TAG, "pagos observados: $_payments")
            val totalToValidate = viewModel.paymentDetails.value?.cash?.value ?: 0.0
            val totalPayments = (viewModel.cashPayments.value?.fold(0.0, { sum, _payment ->
                sum + (_payment.detail?.amount ?: 0.0)
            }) ?: 0.0)
            val toValidate = totalToValidate - totalPayments
            totalToValidateTv.text = getString(R.string.total_to_validate, utilities.formatCurrencyNumber(totalToValidate))
            toValidateTv.text = getString(R.string.to_validate, utilities.formatCurrencyNumber(toValidate))
            validatedTv.text = getString(R.string.validated, utilities.formatCurrencyNumber(totalPayments))
            mAdapter?.notifyDataSetChanged()
        })
        addPaymentBtn.setOnClickListener {
            val totalPayments = (viewModel.cashPayments.value?.fold(0.0, { sum, _payment ->
                sum + (_payment.detail?.amount ?: 0.0)
            }) ?: 0.0)
            if(totalPayments < viewModel.paymentDetails.value?.cash?.value ?: 0.0) {
                PaymentValidationDialog.display(parentFragmentManager, null, "Cash")
            } else {
                CclUtilities.getInstance().showAlert(requireActivity(), getString(R.string.error), getString(R.string.all_cash_validated_msg))
            }
        }
        doneBtn.setOnClickListener {
            dismiss()
        }
        initAdapter()
    }

    private fun onRemoveItemCallback() {
        viewModel.cashPayments.postValue(viewModel.cashPayments.value)
    }

    private fun onEditItemCallback(payment: Payment) {
        PaymentValidationDialog.display(parentFragmentManager, payment, "Cash")
    }

    private fun initAdapter(){
        mAdapter = PaymentAdapter(viewModel.cashPayments.value!!, parentFragmentManager)
        mAdapter?.setOnRemoveItemCallback(this::onRemoveItemCallback)
        mAdapter?.setOnEditItemCallback(this::onEditItemCallback)
        paymentsRv.layoutManager = LinearLayoutManager(requireContext())
        paymentsRv.adapter = mAdapter
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
        fun display(fragmentManager: FragmentManager, planificationId: Long): PaymentsValidationListDialog? {
            val previewDialog = PaymentsValidationListDialog(planificationId)
            previewDialog.show(fragmentManager, "payments_validation_list_dialog")
            return previewDialog
        }
    }

    override fun onValueSelected(e: Entry?, h: Highlight?) {

    }

    override fun onNothingSelected() {

    }
}