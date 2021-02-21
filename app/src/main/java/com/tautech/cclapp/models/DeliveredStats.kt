package com.tautech.cclapp.models

import java.io.Serializable

data class DeliveredStats(
    var delivered: Int = 0,
    var unDelivered: Int = 0,
    var partial: Int = 0,
    var pending: Int = 0
    ): Serializable {
    
}