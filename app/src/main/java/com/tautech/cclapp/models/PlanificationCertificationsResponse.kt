package com.tautech.cclapp.models

import java.io.Serializable

data class PlanificationCertificationsResponse(
    val _embedded: PlanificationCertification,
    val _links: Any? = null): Serializable {}

data class PlanificationCertification(
    val planificationCertifications: List<Certification> = listOf()
): Serializable {
}