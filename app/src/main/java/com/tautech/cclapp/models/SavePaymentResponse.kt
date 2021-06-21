package com.tautech.cclapp.models

import java.io.Serializable

data class SavePaymentResponse(
    val id: Long,
    var code: String
): Serializable