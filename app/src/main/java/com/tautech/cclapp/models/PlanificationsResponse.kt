package com.tautech.cclapp.models

data class PlanificationsResponse(
    val content: ArrayList<Planification> = arrayListOf(),
    val pageNumber: Int? = null,
    val pageSize: Int? = null,
    val totalElements: Int? = null,
    val totalPages: Int? = null) {
}