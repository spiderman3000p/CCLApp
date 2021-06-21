package com.tautech.cclapp.models

import java.io.Serializable

data class BanksResponse(
    val _embedded: BanksResponseHolder) {
}

data class BanksResponseHolder(
    val banks: ArrayList<Bank> = arrayListOf()
): Serializable {
}