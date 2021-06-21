package com.tautech.cclapp.models

data class DeliveredItemToUpload(
    var quantityDelivered: Int = 0,
    var amount: Double = 0.0,
    internal var planificationId: Long = 0,
    var deliveryLineId: Long){
}