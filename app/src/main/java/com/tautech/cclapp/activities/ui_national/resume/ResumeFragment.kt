package com.tautech.cclapp.activities.ui_national.resume

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.tautech.cclapp.R
import com.tautech.cclapp.activities.CertificateActivity
import com.tautech.cclapp.activities.CertificateActivityViewModel
import kotlinx.android.synthetic.main.fragment_resume.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread

class ResumeFragment : Fragment() {
    val TAG = "RESUME_FRAGMENT"
    private val viewModel: CertificateActivityViewModel by activityViewModels()
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_resume, container, false)
        viewModel.pendingDeliveryLines.observe(viewLifecycleOwner, Observer{pendingDeliveryLines ->
            partialDeliveryLinesTv.text = viewModel.certifiedDeliveryLines.value?.size.toString()
            uncertifiedDeliveryLinesTv.text = pendingDeliveryLines.size.toString()
            updateDeliveriesCount()
        })
        viewModel.certifiedDeliveryLines.observe(viewLifecycleOwner, Observer{certifiedDeliveryLines ->
            partialDeliveryLinesTv.text = certifiedDeliveryLines.size.toString()
            uncertifiedDeliveryLinesTv.text = viewModel.pendingDeliveryLines.value?.size.toString()
            updateDeliveriesCount()
        })
        viewModel.planification.observe(viewLifecycleOwner, Observer { planification ->
            partialDeliveryLinesTv.text = viewModel.certifiedDeliveryLines.value?.size.toString()
            uncertifiedDeliveryLinesTv.text = viewModel.pendingDeliveryLines.value?.size.toString()
            updateDeliveriesCount()
        })
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as CertificateActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
        (activity as CertificateActivity).supportActionBar?.setDisplayShowHomeEnabled(true)
        initCounters()
        updateDeliveriesCount()
    }

    fun initCounters(){
        partialDeliveriesTv.text = "0"
        uncertifiedDeliveriesTv.text = "0"
        partialDeliveryLinesTv.text = (viewModel.planification.value?.totalCertified ?: 0).toString()
        uncertifiedDeliveryLinesTv.text = ((viewModel.planification.value?.totalDeliveries ?: 0) - (viewModel.planification.value?.totalCertified ?: 0)).toString()
        totalCashDeliveriesTv.text = (viewModel.planification.value?.totalDeliveries ?: 0).toString()
        totalDeliveryLinesTv.text = (viewModel.planification.value?.totalDeliveries ?: 0).toString()
    }

    fun updateDeliveriesCount(){
        Log.i(TAG, "actualizando deliveries count...")
        val deliveries = viewModel.planificationLines.value
        val certifications = viewModel.certifiedDeliveryLines.value
        Log.i(TAG, "deliveries(${deliveries?.size}): $deliveries")
        Log.i(TAG, "certifications(${certifications?.size}): $certifications")
        var pendingDeliveriesCount = 0
        var certifiedDeliveriesCount = 0
        doAsync {
            certifiedDeliveriesCount = deliveries?.count { delivery ->
                (certifications?.count {
                    it.deliveryId == delivery.deliveryId
                } ?: 0) == delivery.totalQuantity
            } ?: 0
            Log.i(TAG, "certified deliveries count: $certifiedDeliveriesCount")
            uiThread {
                pendingDeliveriesCount = (deliveries?.size ?: 0) - certifiedDeliveriesCount
                partialDeliveriesTv.text =
                    certifiedDeliveriesCount.toString()
                uncertifiedDeliveriesTv.text =
                    pendingDeliveriesCount.toString()
            }
        }
    }
}