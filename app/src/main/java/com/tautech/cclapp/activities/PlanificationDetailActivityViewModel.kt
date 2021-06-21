package com.tautech.cclapp.activities

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tautech.cclapp.models.*

class PlanificationDetailActivityViewModel() : ViewModel() {
    val planification = MutableLiveData<Planification>()
    val deliveries = MutableLiveData<MutableList<Delivery>>()
    val stateFormDefinitions = MutableLiveData<MutableList<StateFormDefinition>>()
    val paymentDetails = MutableLiveData<PlanificationPaymentDetails>()
    var cashPayments = MutableLiveData<MutableList<Payment>>()
    val legalizedComplete = MutableLiveData<Boolean>()
    init {
        cashPayments.setValue(mutableListOf())
        legalizedComplete.setValue(false)
    }
}