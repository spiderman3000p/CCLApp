package com.tautech.cclapp.models

import java.io.Serializable

data class PlanificationsResponse(
    val _embedded: PlanificationResponseHolder) {
}

data class PlanificationResponseHolder(
    val planificationVO2s: ArrayList<Planification> = arrayListOf()
): Serializable {
}