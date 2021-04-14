package com.tautech.cclapp.activities

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tautech.cclapp.models.*
const val TAG = "ManageDeliveryActivityViewModel"
class ManageDeliveryActivityViewModel() : ViewModel() {
    val showPaymentMethod = MutableLiveData<Boolean>()
    val paymentMethods = MutableLiveData<MutableList<PaymentMethod>?>()
    val selectedPaymentMethod = MutableLiveData<PaymentMethod?>()
    val planification = MutableLiveData<Planification?>()
    val delivery = MutableLiveData<Delivery?>()
    var deliveryLines = MutableLiveData<MutableList<DeliveryLine>?>()
    val stateFormDefinition = MutableLiveData<StateFormDefinition?>()
    val state = MutableLiveData<String?>()
    val changedDeliveryLine = MutableLiveData<DeliveryLine>()

    override fun onCleared() {
        super.onCleared()
        Log.i(TAG, "onCleared() view model cleared")
    }

    fun clear(){
        Log.i(TAG, "clear() Solicitando limpieza de view model...")
        this.clear()
    }
}