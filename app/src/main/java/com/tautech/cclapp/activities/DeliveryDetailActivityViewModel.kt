package com.tautech.cclapp.activities

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tautech.cclapp.models.Delivery
import com.tautech.cclapp.models.DeliveryLine
import com.tautech.cclapp.models.Planification

class DeliveryDetailActivityViewModel() : ViewModel() {
    val planification = MutableLiveData<Planification>()
    val delivery = MutableLiveData<Delivery>()
    val deliveryLines = MutableLiveData<MutableList<DeliveryLine>>()
    //val stateFormDefinitions = MutableLiveData<MutableList<StateFormDefinition>>()
}