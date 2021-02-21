package com.tautech.cclapp.activities

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tautech.cclapp.models.DeliveryLine
import com.tautech.cclapp.models.Planification
import com.tautech.cclapp.models.PlanificationLine

class PlanificationsActivityViewModel() : ViewModel() {
    val planifications = MutableLiveData<MutableList<Planification>>()

    init {
        this.planifications.postValue(mutableListOf())
    }
}