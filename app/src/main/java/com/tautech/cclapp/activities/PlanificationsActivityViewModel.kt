package com.tautech.cclapp.activities

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tautech.cclapp.models.Planification

class PlanificationsActivityViewModel() : ViewModel() {
    val planifications = MutableLiveData<MutableList<Planification>>()
}