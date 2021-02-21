package com.tautech.cclapp.activities

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tautech.cclapp.models.DeliveryLine
import com.tautech.cclapp.models.Planification
import com.tautech.cclapp.models.PlanificationLine
import com.tautech.cclapp.models.StateFormDefinition

class ManageDeliveryActivityViewModel() : ViewModel() {
    val planification = MutableLiveData<Planification?>()
    val delivery = MutableLiveData<PlanificationLine?>()
    val stateFormDefinition = MutableLiveData<StateFormDefinition?>()
    val state = MutableLiveData<String>()
}