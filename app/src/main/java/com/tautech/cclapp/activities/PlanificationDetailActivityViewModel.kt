package com.tautech.cclapp.activities

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tautech.cclapp.models.DeliveryLine
import com.tautech.cclapp.models.Planification
import com.tautech.cclapp.models.PlanificationLine
import com.tautech.cclapp.models.StateFormDefinition

class PlanificationDetailActivityViewModel() : ViewModel() {
    val planification = MutableLiveData<Planification>()
    val deliveries = MutableLiveData<MutableList<PlanificationLine>>()
    val stateFormDefinitions = MutableLiveData<MutableList<StateFormDefinition>>()
    //val procesedDeliveries = MutableLiveData<MutableList<PlanificationLine>>()
    val deliveryLines = MutableLiveData<MutableList<DeliveryLine>>()
    //val pendingDeliveryLines = MutableLiveData<MutableList<DeliveryLine>>()
    init {
        this.deliveries.postValue(mutableListOf())
        this.deliveryLines.postValue(mutableListOf())
        //this.procesedDeliveryLines.postValue(mutableListOf())
        //this.pendingDeliveryLines.postValue(mutableListOf())
    }
}