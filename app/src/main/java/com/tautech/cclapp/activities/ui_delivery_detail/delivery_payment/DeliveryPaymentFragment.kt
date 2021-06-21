package com.tautech.cclapp.activities.ui_delivery_detail.delivery_payment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.tautech.cclapp.R
import com.tautech.cclapp.classes.DeliveryPaymentFragmentDialog
import com.tautech.cclapp.activities.ManageDeliveryActivity
import com.tautech.cclapp.activities.ManageDeliveryActivityViewModel
import com.tautech.cclapp.adapters.PaymentAdapter
import com.tautech.cclapp.classes.AuthStateManager
import com.tautech.cclapp.classes.CclUtilities
import com.tautech.cclapp.models.Payment
import kotlinx.android.synthetic.main.fragment_delivery_payment_list.*

class DeliveryPaymentFragment(): Fragment() {
  val TAG = "DELIVERY_PAYMENT_METHOD_FRAGMENT"
  private var mStateManager: AuthStateManager? = null
  private var mAdapter: PaymentAdapter? = null
  private lateinit var viewModel: ManageDeliveryActivityViewModel

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View? {
    val root = inflater.inflate(R.layout.fragment_delivery_payment_list, container, false)
    Log.i(TAG, "onCreateView DeliveryFormFragment")
    mStateManager = AuthStateManager.getInstance(requireContext())
    if (!mStateManager!!.current.isAuthorized) {
      CclUtilities.getInstance().showAlert(requireActivity(),"Error", "Su sesion ha expirado", this::signOut)
    }
    val _viewModel: ManageDeliveryActivityViewModel by activityViewModels()
    viewModel = _viewModel
    return root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    (activity as ManageDeliveryActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
    (activity as ManageDeliveryActivity).supportActionBar?.setDisplayShowHomeEnabled(true)
    viewModel.payments.observe(viewLifecycleOwner, Observer { _payments ->
      Log.i(TAG, "pagos observados: $_payments")
      mAdapter?.notifyDataSetChanged()
    })
    addPaymentBtn.setOnClickListener {
      if(activity != null) {
        DeliveryPaymentFragmentDialog.display(activity?.supportFragmentManager!!)
      }
    }
    initAdapter()
  }

  private fun onRemoveItemCallback() {
    viewModel.payments.setValue(viewModel.payments.value)
  }

  private fun onEditItemCallback(payment: Payment) {
    DeliveryPaymentFragmentDialog.display(parentFragmentManager, payment)
  }

  private fun initAdapter(){
    mAdapter = PaymentAdapter(viewModel.payments.value!!, parentFragmentManager)
    mAdapter?.setOnRemoveItemCallback(this::onRemoveItemCallback)
    mAdapter?.setOnEditItemCallback(this::onEditItemCallback)
    paymentsRv.layoutManager = LinearLayoutManager(requireContext())
    paymentsRv.adapter = mAdapter
  }

  private fun signOut() {
    mStateManager?.signOut(requireContext())
  }

  companion object{
    var mInstance: DeliveryPaymentFragment? = null
    fun getInstance(): DeliveryPaymentFragment?{
      if(mInstance == null){
        mInstance = DeliveryPaymentFragment()
      }
      return mInstance
    }
  }
}