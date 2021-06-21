package com.tautech.cclapp.models

import java.io.Serializable

data class PaymentLegalization(
    var planificationId: Long = 0,
    var amount: Double = 0.0,
    var detail: List<PlanificationPaymentDetail>? = null
): Serializable {

}