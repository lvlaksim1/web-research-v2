package ru.evrasia.research

import org.json.JSONObject

internal object CaptureWarning {
    fun create(
        code: String,
        message: String,
        stage: String,
        url: String = "",
        artifact: String = "",
        error: String = "",
        details: JSONObject? = null
    ): JSONObject {
        val warning = JSONObject()
            .put("source", "capture-warning")
            .put("time", System.currentTimeMillis())
            .put("code", code)
            .put("message", message)
            .put("stage", stage)
        if (url.isNotBlank()) warning.put("url", url)
        if (artifact.isNotBlank()) warning.put("artifact", artifact)
        if (error.isNotBlank()) warning.put("error", error)
        if (details != null && details.length() > 0) warning.put("details", details)
        return warning
    }
}
