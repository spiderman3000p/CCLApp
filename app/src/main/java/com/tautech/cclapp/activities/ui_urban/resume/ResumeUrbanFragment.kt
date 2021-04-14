package com.tautech.cclapp.activities.ui_urban.resume

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.tautech.cclapp.R
import com.tautech.cclapp.activities.PlanificationDetailActivity
import com.tautech.cclapp.activities.PlanificationDetailActivityViewModel
import kotlinx.android.synthetic.main.fragment_resume_urban.*

class ResumeUrbanFragment : Fragment() {
    val TAG = "RESUME_URBAN_FRAGMENT"
    private val viewModel: PlanificationDetailActivityViewModel by activityViewModels()
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_resume_urban, container, false)
        viewModel.deliveries.observe(viewLifecycleOwner, Observer{
            initCounters()
        })
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.actionBar?.setDisplayHomeAsUpEnabled(true)
        activity?.actionBar?.setDisplayShowHomeEnabled(true)
        initCounters()
    }

    fun initCounters(){
        val totalDeliveryLines = viewModel.planification.value?.totalUnits ?: 0
        val totalDeliveries = viewModel.planification.value?.totalDeliveries ?: 0
        deliveredDeliveriesTv?.text = viewModel.planification.value?.totalDelivered.toString()
        undeliveredDeliveriesTv?.text = (totalDeliveries - (viewModel.planification.value?.totalDelivered ?: 0)).toString()
        deliveredDeliveryLinesTv?.text = viewModel.planification.value?.totalDeliveredLines.toString()
        undeliveredDeliveryLinesTv?.text = (totalDeliveryLines - (viewModel.planification.value?.totalDelivered ?: 0)).toString()
        totalDeliveriesTv2?.text = totalDeliveries.toString()
        totalDeliveryLinesTv2?.text = totalDeliveryLines.toString()
    }
}