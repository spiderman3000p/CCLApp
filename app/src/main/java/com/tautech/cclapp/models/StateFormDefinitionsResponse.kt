package com.tautech.cclapp.models

import org.json.JSONObject
import java.io.Serializable

data class StateFormDefinitionsResponse(
    val _embedded: StateFormDefinitionHolder,
    val _links: JSONObject? = null): Serializable {}

data class StateFormDefinitionHolder(
    val stateFormDefinitions: ArrayList<StateFormDefinition> = arrayListOf()
): Serializable {
}