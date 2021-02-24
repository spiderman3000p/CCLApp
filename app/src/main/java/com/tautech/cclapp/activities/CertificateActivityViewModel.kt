package com.tautech.cclapp.activities

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tautech.cclapp.models.Delivery
import com.tautech.cclapp.models.DeliveryLine
import com.tautech.cclapp.models.Planification

class CertificateActivityViewModel() : ViewModel() {
    val planification = MutableLiveData<Planification>()
    val planificationLines = MutableLiveData<MutableList<Delivery>>()
    val pendingDeliveryLines = MutableLiveData<MutableList<DeliveryLine>>()
    val certifiedDeliveryLines = MutableLiveData<MutableList<DeliveryLine>>()

    init {
        this.pendingDeliveryLines.postValue(mutableListOf())
        this.certifiedDeliveryLines.postValue(mutableListOf())
        this.planificationLines.postValue(mutableListOf())
    }
}