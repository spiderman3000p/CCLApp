package com.tautech.cclapp.activities

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tautech.cclapp.models.DeliveryLine
import com.tautech.cclapp.models.Planification
import com.tautech.cclapp.models.PlanificationLine
import com.tautech.cclapp.models.StateFormDefinition

class DeliveryDetailActivityViewModel() : ViewModel() {
    val planification = MutableLiveData<Planification>()
    val delivery = MutableLiveData<PlanificationLine>()
    val procesedDeliveryLines = MutableLiveData<MutableList<DeliveryLine>>()
    //val stateFormDefinitions = MutableLiveData<MutableList<StateFormDefinition>>()
}