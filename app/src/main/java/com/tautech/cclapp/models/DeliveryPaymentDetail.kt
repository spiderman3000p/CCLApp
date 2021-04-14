package com.tautech.cclapp.models

data class DeliveryPaymentDetail(
    val id: Int? = null,
    var deliveryId: Long? = null,
    var amount: Double? = null,
    var transactionNumber: String? = null,
    var urlPhoto: String? = null,
    var notes: String? = null,
    var paymentMethod: String? = null
)