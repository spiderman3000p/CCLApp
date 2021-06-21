package com.tautech.cclapp.models

import android.net.Uri
import androidx.room.*
import java.io.File
import java.io.Serializable

@Entity
data class Payment(
    @PrimaryKey(autoGenerate = true)
    var id: Long? = null,
    @Embedded
    var detail: PlanificationPaymentDetail? = null,
    @Ignore
    var file: File? = null,
    var fileAbsolutePath: String? = null,
    @Ignore
    var fileUri: Uri? = null,
    var fileUriStr: String? = null
): Serializable