package com.tautech.cclapp.models

import java.io.Serializable

data class PaymentMethodsResponse(
    val _embedded: PaymentMethodsResponseHolder) {
}

data class PaymentMethodsResponseHolder(
    val paymentMethods: ArrayList<PaymentMethod> = arrayListOf()
): Serializable {
}