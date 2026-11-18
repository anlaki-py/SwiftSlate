package com.musheer360.swiftslate.api

import com.musheer360.swiftslate.feature.google.domain.GoogleException
import com.musheer360.swiftslate.feature.google.domain.GoogleFailure
import org.json.JSONArray
import org.json.JSONObject

/** Gemini text protocol shared by Google-account and Gemini API-key requests. */
internal object GeminiProtocol {
    fun request(prompt: String, text: String, temperature: Double): JSONObject = JSONObject().apply {
        put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject()
            .put("text", ApiClientUtils.SYSTEM_PROMPT_PREFIX + prompt))))
        put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray()
            .put(JSONObject().put("text", "---BEGIN TEXT---\n$text\n---END TEXT---")))))
        put("generationConfig", JSONObject().put("temperature", temperature.coerceIn(0.0, 2.0)))
    }

    fun modelIds(root: JSONObject): List<String> {
        val models = root.opt("models")
        return when (models) {
            is JSONObject -> models.keys().asSequence().filter { isTextModel(it) &&
                models.optJSONObject(it)?.optBoolean("isDisabled", false) != true }.toList()
            is JSONArray -> (0 until models.length()).mapNotNull { index ->
                val model = models.optJSONObject(index) ?: return@mapNotNull null
                val methods = model.optJSONArray("supportedGenerationMethods")
                model.optString("name").removePrefix("models/").takeIf { isTextModel(it) &&
                    (methods == null || (0 until methods.length()).any { i -> methods.optString(i) == "generateContent" }) }
            }
            else -> throw GoogleException(GoogleFailure.INVALID_RESPONSE)
        }.distinct().sorted()
    }

    fun text(body: String): String {
        val output = StringBuilder()
        fun append(root: JSONObject) {
            val response = root.optJSONObject("response") ?: root
            val block = response.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            if (block.isNotBlank() && block != "BLOCK_REASON_UNSPECIFIED") throw GoogleException(GoogleFailure.BLOCKED)
            val candidate = response.optJSONArray("candidates")?.optJSONObject(0) ?: return
            when (val finish = candidate.optString("finishReason")) {
                "MAX_TOKENS" -> throw GoogleException(GoogleFailure.TRUNCATED)
                "", "STOP", "FINISH_REASON_UNSPECIFIED" -> Unit
                else -> throw GoogleException(if (finish == "OTHER") GoogleFailure.INVALID_RESPONSE else GoogleFailure.BLOCKED)
            }
            val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: return
            for (index in 0 until parts.length()) {
                val part = parts.getJSONObject(index)
                if (!part.optBoolean("thought", false)) output.append(part.optString("text", ""))
            }
        }
        if (body.trimStart().startsWith("{")) {
            append(JSONObject(body))
        } else {
            val event = StringBuilder()
            fun flush() {
                val data = event.toString().trim()
                if (data.isNotBlank() && data != "[DONE]") append(JSONObject(data))
                event.setLength(0)
            }
            body.lineSequence().forEach { line ->
                if (line.isBlank()) flush()
                else if (line.startsWith("data:")) event.append(line.removePrefix("data:").trimStart()).append('\n')
            }
            flush()
        }
        val text = ApiClientUtils.stripMarkdownFences(output.toString())
        if (text.isBlank()) throw GoogleException(GoogleFailure.INVALID_RESPONSE)
        return text
    }

    fun remainingFraction(root: JSONObject): Double? {
        val models = root.optJSONObject("models") ?: return null
        val fractions = mutableListOf<Double>()
        for (id in models.keys()) {
            if (!isTextModel(id)) continue
            val model = models.optJSONObject(id) ?: continue
            for (key in listOf("quotaInfo", "quotaInfos", "dailyQuotaInfo", "weeklyQuotaInfo")) {
                val value = model.opt(key)
                val infos = if (value is JSONArray) (0 until value.length()).mapNotNull(value::optJSONObject)
                    else listOfNotNull(value as? JSONObject)
                infos.forEach { info -> if (info.has("remainingFraction")) {
                    val fraction = info.optDouble("remainingFraction", Double.NaN)
                    if (fraction.isFinite()) fractions.add(fraction.coerceIn(0.0, 1.0))
                } }
            }
        }
        return fractions.minOrNull()
    }

    private fun isTextModel(id: String): Boolean = id.startsWith("gemini-", true) &&
        listOf("image", "embedding", "tts", "live", "audio").none { it in id.lowercase() }
}
