package com.tautech.cclapp.activities

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tautech.cclapp.models.*

class PlanificationDetailActivityViewModel() : ViewModel() {
    val planification = MutableLiveData<Planification>()
    val deliveries = MutableLiveData<MutableList<Delivery>>()
    val stateFormDefinitions = MutableLiveData<MutableList<StateFormDefinition>>()
}