package com.tautech.cclapp.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable
import java.util.*
@Entity
data class CashValidationDetail(
    @PrimaryKey
    var code: String = UUID.randomUUID().toString(),
    var amount: Double? = null,
    var transactionNumber: String? = null,
    var bankId: Long? = null,
    var transactionType: String? = null,
    var hasPhoto: Boolean? = null
): Serializable