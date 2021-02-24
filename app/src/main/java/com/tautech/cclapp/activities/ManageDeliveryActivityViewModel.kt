package com.tautech.cclapp.activities

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tautech.cclapp.models.*

class ManageDeliveryActivityViewModel() : ViewModel() {
    val planification = MutableLiveData<Planification>()
    val delivery = MutableLiveData<Delivery?>()
    val deliveryLines = MutableLiveData<MutableList<DeliveryLine>>()
    val stateFormDefinition = MutableLiveData<StateFormDefinition?>()
    val state = MutableLiveData<String>()
}