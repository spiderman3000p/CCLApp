package com.tautech.cclapp.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable
import java.util.*

@Entity
data class PendingToUploadPayment(
    @PrimaryKey
    var code: String = UUID.randomUUID().toString(),
    var amount: Double? = null,
    var transactionNumber: String? = null,
    var paymentMethodId: Long? = null,
    var hasPhoto: Boolean? = null
    //var paymentMethod: String? = null
): Serializable