package com.tautech.cclapp.classes

import java.lang.StringBuilder
import java.util.*

class CclUtilities {
    /**
     *  @author Rafael Cardona
     *  @description Esta funcion formatea los numeros grandes a su version resumida usando K para los miles,
     *  M para los millones, B para los billones y T para los trillones
     */
    fun formatQuantity(value: Double): String {
        val sb = StringBuilder()
        val formatter = Formatter(sb, Locale.US)
        return when {
            value > 999999999999 -> {
                formatter.format("%,.2f", value / 1000000000000).toString() + "T"
            }
            value > 999999999 -> {
                formatter.format("%,.2f", value / 1000000000).toString() + "B"
            }
            value > 999999 -> {
                formatter.format("%,.2f", value / 1000000).toString() + "M"
            }
            value <= 999999 -> {
                formatter.format("%,.2f", value / 1000).toString() + "K"
            }
            value <= 999 -> {
                "$value"
            }
            else -> {
                "0"
            }
        }
    }

    companion object{
        private var instance: CclUtilities? = null
        fun getInstance(): CclUtilities{
            if (instance == null){
                instance = CclUtilities()
            }
            return instance!!
        }
    }
}