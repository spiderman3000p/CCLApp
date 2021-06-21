package com.tautech.cclapp.models

data class PaymentMethod(
    val id: Long? = null,
    var code: String? = null,
    var description: String? = null,
    var requiresPhoto: Boolean? = false,
    var requiresTransactionNumber: Boolean? = false
)