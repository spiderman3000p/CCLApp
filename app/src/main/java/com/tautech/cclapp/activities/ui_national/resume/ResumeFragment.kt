package com.tautech.cclapp.activities.ui_national.resume

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.tautech.cclapp.*
import com.tautech.cclapp.activities.CertificateActivity
import com.tautech.cclapp.activities.CertificateActivityViewModel
import kotlinx.android.synthetic.main.fragment_resume.*
import kotlinx.android.synthetic.main.fragment_resume.certifiedDeliveriesTv
import kotlinx.android.synthetic.main.fragment_resume.certifiedDeliveryLinesTv

class ResumeFragment : Fragment() {
    val TAG = "RESUME_FRAGMENT"
    private val viewModel: CertificateActivityViewModel by activityViewModels()
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_resume, container, false)
        viewModel.certifiedDeliveryLines.observe(viewLifecycleOwner, Observer{certifiedDeliveryLines ->
            certifiedDeliveryLinesTv.text =
                certifiedDeliveryLines.size.toString()
            uncertifiedDeliveryLinesTv.text = (viewModel.planification.value?.totalLines?.minus(
                    certifiedDeliveryLines.size) ?: 0).toString()
            updateDeliveriesCount()
        })
        viewModel.planification.observe(viewLifecycleOwner, Observer { planification ->
            certifiedDeliveryLinesTv.text =
                viewModel.certifiedDeliveryLines.value?.size.toString()
            uncertifiedDeliveryLinesTv.text = (viewModel.planification.value?.totalLines?.minus(
                viewModel.certifiedDeliveryLines.value?.size ?: 0) ?: 0).toString()
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
        certifiedDeliveriesTv.text = "0"
        uncertifiedDeliveriesTv.text = "0"
        certifiedDeliveryLinesTv.text = (viewModel.planification.value?.totalCertificate ?: 0).toString()
        uncertifiedDeliveryLinesTv.text = ((viewModel.planification.value?.totalDeliveries ?: 0) - (viewModel.planification.value?.totalCertificate ?: 0)).toString()
        totalDeliveriesTv.text = (viewModel.planification.value?.totalDeliveries ?: 0).toString()
        totalDeliveryLinesTv.text = (viewModel.planification.value?.totalLines ?: 0).toString()
    }

    fun updateDeliveriesCount(){
        val deliveries = viewModel.planificationLines.value
        val certifications = viewModel.certifiedDeliveryLines.value
        var pendingDeliveriesCount = deliveries?.size ?: 0
        var certifiedDeliveriesCount = 0
        var aux = 0
        if (deliveries != null) {
            for (delivery in deliveries) {
                aux = certifications?.count {
                    it.deliveryId == delivery.id
                } ?: 0
                if (aux == delivery.totalQuantity) {
                    certifiedDeliveriesCount++
                }
            }
            pendingDeliveriesCount = deliveries.size - certifiedDeliveriesCount
        }
        certifiedDeliveriesTv.text =
            certifiedDeliveriesCount.toString()
        uncertifiedDeliveriesTv.text =
            pendingDeliveriesCount.toString()
    }
}