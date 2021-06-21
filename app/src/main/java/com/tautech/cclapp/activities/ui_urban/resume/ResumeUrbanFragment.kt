package com.tautech.cclapp.activities.ui_urban.resume

import android.database.sqlite.SQLiteAccessPermException
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.tautech.cclapp.R
import com.tautech.cclapp.activities.PlanificationDetailActivity
import com.tautech.cclapp.activities.PlanificationDetailActivityViewModel
import com.tautech.cclapp.database.AppDatabase
import kotlinx.android.synthetic.main.fragment_resume_urban.*

class ResumeUrbanFragment: Fragment() {
    val TAG = "RESUME_URBAN_FRAGMENT"
    var db: AppDatabase? = null
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
        try {
            db = AppDatabase.getDatabase(requireContext())
        } catch(ex: SQLiteDatabaseLockedException) {
            Log.e(TAG, "Database error found", ex)
        } catch (ex: SQLiteAccessPermException) {
            Log.e(TAG, "Database error found", ex)
        } catch (ex: SQLiteCantOpenDatabaseException) {
            Log.e(TAG, "Database error found", ex)
        }
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as PlanificationDetailActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
        (activity as PlanificationDetailActivity).supportActionBar?.setDisplayShowHomeEnabled(true)
        initCounters()
    }

    fun initCounters(){
        val totalDeliveryLines = viewModel.planification.value?.totalLines ?: 0
        val totalDeliveries = viewModel.planification.value?.totalDeliveries ?: 0
        val totalDelivered = viewModel.planification.value?.totalDelivered ?: 0
        val totalPartial = viewModel.planification.value?.totalPartial ?: 0
        val totalUndelivered = viewModel.planification.value?.totalUndelivered ?: 0
        val totalDeliveredLines = viewModel.planification.value?.totalDeliveredLines ?: 0
        val totalUnits = viewModel.planification.value?.totalUnits ?: 0
        val totalDeliveredUnits = viewModel.planification.value?.totalDeliveredUnits ?: 0
        val totalUndeliveredUnits = totalUnits - totalDeliveredUnits
        partialDeliveriesTv?.text = totalPartial.toString()
        deliveredDeliveriesTv?.text = totalDelivered.toString()
        undeliveredDeliveriesTv?.text = totalUndelivered.toString()
        totalDeliveriesTv2?.text = totalDeliveries.toString()
        totalUnitsTv2?.text = totalUnits.toString()
        undeliveredUnitsTv?.text = totalUndeliveredUnits.toString()
        deliveredUnitsTv?.text = totalDeliveredUnits.toString()
    }

    override fun onResume() {
        super.onResume()
        // TODO: fetch planification details
    }
}