package com.tautech.cclapp.models

data class DeliveredItemToUpload(
    var quantity: Int = 0,
    var price: Double = 0.0,
    var deliveryLineId: Long){
}