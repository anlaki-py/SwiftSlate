package com.musheer360.swiftslate.model

import org.json.JSONObject

data class AiProvider(
    val id: String,
    val name: String,
    val endpoint: String,
    val selectedModel: String = ""
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("endpoint", endpoint)
        obj.put("selectedModel", selectedModel)
        return obj
    }

    companion object {
        fun fromJson(json: JSONObject): AiProvider {
            return AiProvider(
                id = json.getString("id"),
                name = json.getString("name"),
                endpoint = json.getString("endpoint"),
                selectedModel = json.optString("selectedModel", "")
            )
        }
    }
}
