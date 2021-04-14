package com.tautech.cclapp.models

data class Customer(
    val id: String,
    var identificationType: String?,
    var identificationNumber: String?,
    var name: String?,
    var email: String?,
    var phone: String?,
    var contactName: String?,
    var contactPhone: String?,
    var contactEmail: String?,
    var showPaymentMethod: Boolean?
)