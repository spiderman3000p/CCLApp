package com.tautech.cclapp.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable
import java.util.*

@Entity
data class PendingToUploadLegalization(
    @PrimaryKey
    var code: String = UUID.randomUUID().toString(),
    var amount: Double? = null,
    var transactionNumber: String? = null,
    var bankId: Long? = null,// used only in legalization
    var paymentMethodId: Long? = null,
    var paymentMethod: String? = null,
    var transactionType: String? = null,// used only in legalization
    var hasPhoto: Boolean? = null
): Serializable