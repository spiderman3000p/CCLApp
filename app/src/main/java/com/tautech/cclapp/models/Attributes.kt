package com.tautech.cclapp.models

import java.io.Serializable

data class Attributes(
    var driverId: List<Int>? = null,
    var userType: List<String>? = null): Serializable {
    
}