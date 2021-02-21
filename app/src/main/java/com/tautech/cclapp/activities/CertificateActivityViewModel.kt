package com.tautech.cclapp.activities

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tautech.cclapp.models.DeliveryLine
import com.tautech.cclapp.models.Planification
import com.tautech.cclapp.models.PlanificationLine

class CertificateActivityViewModel() : ViewModel() {
    val planification = MutableLiveData<Planification>()
    val planificationLines = MutableLiveData<MutableList<PlanificationLine>>()
    val pendingDeliveryLines = MutableLiveData<MutableList<DeliveryLine>>()
    val certifiedDeliveryLines = MutableLiveData<MutableList<DeliveryLine>>()

    init {
        this.pendingDeliveryLines.postValue(mutableListOf())
        this.certifiedDeliveryLines.postValue(mutableListOf())
        this.planificationLines.postValue(mutableListOf())
    }
}