package com.tautech.cclapp.activities

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tautech.cclapp.models.*
const val TAG = "ManageDeliveryActivityViewModel"
class ManageDeliveryActivityViewModel() : ViewModel() {
    val showPaymentMethod = MutableLiveData<Boolean>()
    val paymentMethods = MutableLiveData<MutableList<PaymentMethod>?>()
    val planification = MutableLiveData<Planification?>()
    val delivery = MutableLiveData<Delivery?>()
    var deliveryLines = MutableLiveData<MutableList<DeliveryLine>?>()
    var payments = MutableLiveData<MutableList<Payment>?>()
    //val paidAmount = MutableLiveData<Double?>()
    val stateFormDefinition = MutableLiveData<StateFormDefinition?>()
    val currentDeliveryState = MutableLiveData<String?>()

    init{
        payments.postValue(mutableListOf())
    }
}