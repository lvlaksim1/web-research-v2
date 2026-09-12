package ru.evrasia.research

import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal class ForensicExportBuilder(private val archive: ResearchArchive) {
    fun logicalRequests(): JSONArray {
        val requests = linkedMapOf<String, JSONObject>()
        synchronized(archive) {
            for (index in 0 until archive.records.length()) {
                val record = archive.records.optJSONObject(index) ?: continue
                val requestId = record.optString("requestId", "")
                if (requestId.isBlank()) continue

                val request = requests.getOrPut(requestId) {
                    JSONObject()
                        .put("requestId", requestId)
                        .put("method", record.optString("method", "GET"))
                        .put("url", record.optString("url", record.optString("page", "")))
                        .put("requestSource", record.optString("requestSource", "unknown"))
                        .put("trafficClass", record.optString("trafficClass", "browser"))
                        .put("time", record.optLong("time", 0L))
                        .put("timestamp", record.optString("timestamp", ""))
                        .put("evidence", JSONArray())
                        .put("captureStatus", "partial")
                }

                request.getJSONArray("evidence").put(
                    JSONObject()
                        .put("eventId", record.optString("eventId", ""))
                        .put("source", record.optString("source", ""))
                        .put("time", record.optLong("time", 0L))
                        .put("timestamp", record.optString("timestamp", ""))
                        .put("status", if (record.has("status")) record.optInt("status") else JSONObject.NULL)
                )

                copyIfUseful(record, request, "method")
                copyIfUseful(record, request, "url")
                copyIfUseful(record, request, "finalUrl")
                copyIfUseful(record, request, "requestHeaders")
                if (!request.has("requestHeaders") && record.has("headers")) {
                    record.optJSONObject("headers")?.let { request.put("requestHeaders", JSONObject(it.toString())) }
                }
                copyIfUseful(record, request, "requestMimeType")
                copyIfUseful(record, request, "requestBody")
                copyIfUseful(record, request, "requestBodyEncoding")
                copyIfUseful(record, request, "requestBodyParts")
                copyIfUseful(record, request, "status")
                copyIfUseful(record, request, "statusText")
                copyIfUseful(record, request, "responseHeaders")
                copyIfUseful(record, request, "responseHeadersRaw")
                copyIfUseful(record, request, "responseBody")
                copyIfUseful(record, request, "responseBodyEncoding")
                copyIfUseful(record, request, "responseSize")
                copyIfUseful(record, request, "mimeType")
                copyIfUseful(record, request, "duration")
                copyIfUseful(record, request, "initiator")
                copyIfUseful(record, request, "initiatorStack")
                copyIfUseful(record, request, "cookieSnapshot")
                copyIfUseful(record, request, "cookieSnapshotSource")
                copyIfUseful(record, request, "redirectChainId")
                copyIfUseful(record, request, "redirectChainStatus")
                copyIfUseful(record, request, "redirectChain")
                copyIfUseful(record, request, "derivedFromRequestId")
                copyIfUseful(record, request, "relatedActionId")
                copyIfUseful(record, request, "httpVersion")
                copyIfUseful(record, request, "timing")

                val source = record.optString("source", "")
                val hasResponse = record.has("status") || record.has("responseBody") || record.has("error")
                if (hasResponse && source in setOf("fetch", "xhr")) {
                    request.put("captureStatus", if (record.has("responseBody")) "complete" else "partial")
                } else if (hasResponse && record.optString("trafficClass") == "derivative") {
                    request.put("captureStatus", "derivative")
                }
            }
        }

        requests.values.forEach { request ->
            if (request.optString("captureStatus") == "partial") {
                request.put(
                    "captureNote",
                    if (request.optString("trafficClass") == "browser")
                        "WebView does not expose response headers/body for every pass-through request; available browser evidence is preserved."
                    else
                        "Only the evidence exposed by the originating API was available."
                )
            }
        }

        val out = JSONArray()
        requests.values
            .sortedWith(compareBy<JSONObject> { it.optLong("time", Long.MAX_VALUE) }.thenBy { it.optString("requestId") })
            .forEach(out::put)
        return out
    }

    fun timeline(): JSONArray {
        val rows = mutableListOf<JSONObject>()
        synchronized(archive) {
            for (index in 0 until archive.records.length()) {
                val record = archive.records.optJSONObject(index) ?: continue
                val item = JSONObject()
                    .put("time", record.optLong("time", 0L))
                    .put("timestamp", record.optString("timestamp", ""))
                    .put("elapsedRealtimeMs", if (record.has("elapsedRealtimeMs")) record.optLong("elapsedRealtimeMs") else JSONObject.NULL)
                    .put("type", timelineType(record))
                    .put("source", record.optString("source", "unknown"))
                    .put("eventId", record.optString("eventId", ""))
                copyIfUseful(record, item, "actionId")
                copyIfUseful(record, item, "requestId")
                copyIfUseful(record, item, "mutationId")
                copyIfUseful(record, item, "relatedActionId")
                copyIfUseful(record, item, "relatedRequestId")
                copyIfUseful(record, item, "url")
                copyIfUseful(record, item, "page")
                copyIfUseful(record, item, "method")
                copyIfUseful(record, item, "status")
                copyIfUseful(record, item, "action")
                copyIfUseful(record, item, "event")
                copyIfUseful(record, item, "message")
                rows.add(item)
            }
        }
        rows.sortWith(compareBy<JSONObject> { it.optLong("time", 0L) }.thenBy { it.optString("eventId", "") })
        return JSONArray(rows)
    }

    fun actionsWithCorrelations(requests: JSONArray = logicalRequests()): JSONArray {
        val actionCopies = linkedMapOf<String, JSONObject>()
        val requestByAction = linkedMapOf<String, LinkedHashSet<String>>()
        val mutationByAction = linkedMapOf<String, LinkedHashSet<String>>()

        synchronized(archive) {
            for (index in 0 until archive.records.length()) {
                val record = archive.records.optJSONObject(index) ?: continue
                if (record.optString("source") == "user-action") {
                    val id = record.optString("actionId", "")
                    if (id.isNotBlank()) actionCopies[id] = JSONObject(record.toString())
                }
                val actionId = record.optString("relatedActionId", "")
                if (actionId.isNotBlank()) {
                    record.optString("requestId", "").takeIf { it.isNotBlank() }?.let {
                        requestByAction.getOrPut(actionId) { linkedSetOf() }.add(it)
                    }
                    record.optString("mutationId", "").takeIf { it.isNotBlank() }?.let {
                        mutationByAction.getOrPut(actionId) { linkedSetOf() }.add(it)
                    }
                }
            }
        }

        for (index in 0 until requests.length()) {
            val request = requests.optJSONObject(index) ?: continue
            val actionId = request.optString("relatedActionId", "")
            if (actionId.isNotBlank()) {
                requestByAction.getOrPut(actionId) { linkedSetOf() }.add(request.optString("requestId"))
            }
        }

        val out = JSONArray()
        actionCopies.values.sortedBy { it.optLong("time", 0L) }.forEach { action ->
            val id = action.optString("actionId")
            action.put("relatedRequestIds", JSONArray(requestByAction[id]?.toList() ?: emptyList<String>()))
            action.put("relatedDomMutationIds", JSONArray(mutationByAction[id]?.toList() ?: emptyList<String>()))
            out.put(action)
        }
        return out
    }

    fun correlations(requests: JSONArray = logicalRequests(), actions: JSONArray = actionsWithCorrelations(requests)): JSONObject {
        val rows = JSONArray()
        for (index in 0 until actions.length()) {
            val action = actions.getJSONObject(index)
            rows.put(
                JSONObject()
                    .put("actionId", action.optString("actionId"))
                    .put("time", action.optLong("time", 0L))
                    .put("type", action.optString("action", ""))
                    .put("relatedRequestIds", action.optJSONArray("relatedRequestIds") ?: JSONArray())
                    .put("relatedDomMutationIds", action.optJSONArray("relatedDomMutationIds") ?: JSONArray())
            )
        }
        return JSONObject()
            .put("format", "web-research-correlations")
            .put("formatVersion", 3)
            .put("actions", rows)
    }

    fun redirects(): JSONArray {
        val out = JSONArray()
        synchronized(archive) {
            for (index in 0 until archive.records.length()) {
                val record = archive.records.optJSONObject(index) ?: continue
                val chain = record.optJSONArray("redirectChain") ?: continue
                if (chain.length() == 0) continue
                out.put(
                    JSONObject()
                        .put("redirectChainId", record.optString("redirectChainId", ""))
                        .put("requestId", record.optString("requestId", ""))
                        .put("requestSource", record.optString("requestSource", ""))
                        .put("trafficClass", record.optString("trafficClass", ""))
                        .put("hops", JSONArray(chain.toString()))
                )
            }
        }
        return out
    }

    fun initiators(): JSONArray {
        val out = JSONArray()
        synchronized(archive) {
            for (index in 0 until archive.records.length()) {
                val record = archive.records.optJSONObject(index) ?: continue
                if (!record.has("requestId")) continue
                if (!record.has("initiatorStack") && !record.has("initiator")) continue
                val item = JSONObject()
                    .put("requestId", record.optString("requestId"))
                    .put("eventId", record.optString("eventId", ""))
                copyIfUseful(record, item, "initiator")
                copyIfUseful(record, item, "initiatorStack")
                out.put(item)
            }
        }
        return out
    }

    fun recorderErrors(): JSONArray = sourceLog(setOf("capture-warning", "recorder-error"))
    fun console(): JSONArray = sourceLog(setOf("console", "console-js"))
    fun javascriptErrors(): JSONArray = sourceLog(setOf("js-error", "promise-rejection", "csp-violation", "resource-error"))
    fun webViewEvents(): JSONArray = sourceLog(setOf("webview-event", "navigation", "history", "new-window", "browser-mode", "desktop-viewport"))
    fun intentEvents(): JSONArray = sourceLog(setOf("android-intent", "deep-link"))
    fun lifecycleEvents(): JSONArray = sourceLog(setOf("android-lifecycle"))
    fun websocketEvents(): JSONArray = sourceLog(setOf("websocket-open", "websocket-state", "websocket-send", "websocket-receive", "worker-websocket"))
    fun sseEvents(): JSONArray = sourceLog(setOf("sse-open", "sse-state", "sse-message"))
    fun navigationPerformance(): JSONArray = sourceLog(setOf("navigation-timing"))
    fun resourcePerformance(): JSONArray = sourceLog(setOf("resource-timing", "performance", "long-task"))
    fun detailedMutations(): JSONArray = sourceLog(setOf("dom-mutation", "shadow-root", "custom-element"))

    fun buildHar(requests: JSONArray): JSONObject {
        val entries = JSONArray()
        for (index in 0 until requests.length()) {
            val r = requests.optJSONObject(index) ?: continue
            val method = r.optString("method", "GET")
            if (method == "WS" || method == "SSE") continue
            val url = r.optString("url", "about:blank").ifBlank { "about:blank" }
            val requestBody = r.optString("requestBody", "")
            val request = JSONObject()
                .put("method", method)
                .put("url", url)
                .put("httpVersion", r.optString("httpVersion", ""))
                .put("headers", headersArray(r.optJSONObject("requestHeaders")))
                .put("queryString", queryArray(url))
                .put("cookies", JSONArray())
                .put("headersSize", -1)
                .put("bodySize", if (r.has("requestBody")) requestBody.toByteArray().size else -1)
            if (r.has("requestBody")) {
                request.put(
                    "postData",
                    JSONObject()
                        .put("mimeType", r.optString("requestMimeType", ""))
                        .put("text", requestBody)
                )
            }

            val responseBody = r.optString("responseBody", "")
            val responseHeaders = when {
                r.has("responseHeaders") -> headersArray(r.optJSONObject("responseHeaders"))
                else -> rawHeaders(r.optString("responseHeadersRaw", ""))
            }
            val content = JSONObject()
                .put("size", if (r.has("responseBody")) responseBody.toByteArray().size else r.optLong("responseSize", -1))
                .put("mimeType", r.optString("mimeType", ""))
            if (r.has("responseBody")) content.put("text", responseBody)
            if (r.has("responseBodyFile")) content.put("_bodyFile", r.optString("responseBodyFile"))
            if (r.has("responseBodySha256")) content.put("_sha256", r.optString("responseBodySha256"))

            val response = JSONObject()
                .put("status", r.optInt("status", 0))
                .put("statusText", r.optString("statusText", ""))
                .put("httpVersion", r.optString("httpVersion", ""))
                .put("headers", responseHeaders)
                .put("cookies", JSONArray())
                .put("content", content)
                .put("redirectURL", r.optString("finalUrl", "").takeIf { it != url } ?: "")
                .put("headersSize", -1)
                .put("bodySize", content.optLong("size", -1))

            val started = r.optLong("time", System.currentTimeMillis())
            entries.put(
                JSONObject()
                    .put("startedDateTime", isoUtc(started))
                    .put("time", r.optLong("duration", 0L))
                    .put("request", request)
                    .put("response", response)
                    .put("cache", JSONObject())
                    .put("timings", r.optJSONObject("timing") ?: JSONObject().put("send", 0).put("wait", r.optLong("duration", 0L)).put("receive", 0))
                    .put("_requestId", r.optString("requestId"))
                    .put("_requestSource", r.optString("requestSource", "unknown"))
                    .put("_trafficClass", r.optString("trafficClass", "browser"))
                    .put("_captureStatus", r.optString("captureStatus", "partial"))
            )
        }
        return JSONObject().put(
            "log",
            JSONObject()
                .put("version", "1.2")
                .put("creator", JSONObject().put("name", "Web Research").put("version", "capture-format-3"))
                .put("pages", JSONArray())
                .put("entries", entries)
        )
    }

    fun rootManifest(pageUrl: String, requests: JSONArray): JSONObject {
        val legacy = SessionManifestBuilder(archive).build(pageUrl)
        val sections = JSONObject()
            .put("network", status("partial", "Exact response bodies are captured for instrumented fetch/XHR; generic pass-through WebView responses remain API-limited."))
            .put("javascript", status(if (archive.scripts.isNotEmpty()) "partial" else "unavailable", "External copies are derivative evidence and document-start coverage is best-effort."))
            .put("cookies", status("partial", "CookieManager snapshots include the native cookie jar; exact per-request cookie membership is not exposed by WebView for every request."))
            .put("storage", status(if (archive.snapshot.length() > 0) "partial" else "unavailable", "Snapshots are point-in-time evidence."))
            .put("indexedDb", status(if (archive.extraArtifacts.keys.any { it.contains("indexeddb-") }) "partial" else "unavailable", "Browser serialization and configured limits may restrict values."))
            .put("cacheStorage", status(if (archive.extraArtifacts.keys.any { it.contains("cache-") }) "partial" else "unavailable", "Browser serialization and configured limits may restrict response bodies."))
            .put("console", status("complete", "WebChromeClient console messages and browser-side error hooks are recorded while instrumentation is active."))
            .put("dom", status("partial", "Snapshots and mutation evidence are recorded while instrumentation is active."))
            .put("screenshots", status(if (archive.extraArtifacts.keys.any { it.contains("/screenshots/") }) "partial" else "unavailable", "Screenshots are checkpoint-based, not a continuous video."))
            .put("websocket", status("partial", "Frames visible to JavaScript are recorded; the WebSocket API does not expose handshake response headers."))
            .put("sse", status("partial", "EventSource events are recorded; response headers are not exposed by the EventSource API."))
            .put("android", status("partial", "WebView callbacks and intents observed by this Activity are recorded."))
            .put("dnsTls", status("unavailable", "WebView does not expose full DNS/TLS/certificate metadata for pass-through requests without active network interception."))

        return JSONObject()
            .put("format", "web-research-capture")
            .put("formatVersion", 3)
            .put("captureFormatVersion", 3)
            .put("createdAt", System.currentTimeMillis())
            .put("sessionId", archive.selectedSessionId)
            .put("recordingStartedAt", archive.selectedStartedAt)
            .put("recordingEndedAt", archive.selectedEndedAt)
            .put("page", pageUrl)
            .put("sections", sections)
            .put(
                "statistics",
                JSONObject()
                    .put("rawEvents", archive.records.length())
                    .put("logicalRequests", requests.length())
                    .put("scripts", archive.scripts.size)
                    .put("resources", archive.resources.size)
                    .put("artifacts", archive.extraArtifacts.size)
            )
            .put("legacySessionManifest", legacy)
    }

    private fun sourceLog(sources: Set<String>): JSONArray {
        val out = JSONArray()
        synchronized(archive) {
            for (index in 0 until archive.records.length()) {
                val record = archive.records.optJSONObject(index) ?: continue
                if (record.optString("source", "") in sources) out.put(JSONObject(record.toString()))
            }
        }
        return out
    }

    private fun timelineType(record: JSONObject): String {
        val source = record.optString("source", "")
        return when {
            source == "user-action" -> "user_${record.optString("action", "action")}"
            source == "dom-mutation" -> "dom_mutation"
            source == "fetch" || source == "xhr" || source == "webview" -> "network"
            source.startsWith("websocket-") -> "websocket"
            source.startsWith("sse-") -> "sse"
            source == "console" -> "console"
            source in setOf("js-error", "promise-rejection", "csp-violation", "resource-error") -> "javascript_error"
            source == "android-intent" || source == "deep-link" -> "android_intent"
            source == "android-lifecycle" -> "android_lifecycle"
            source == "capture-warning" -> "recorder_warning"
            else -> source.ifBlank { "event" }
        }
    }

    private fun copyIfUseful(from: JSONObject, to: JSONObject, key: String) {
        if (!from.has(key)) return
        val value = from.opt(key) ?: return
        if (value == JSONObject.NULL) return
        if (value is String && value.isBlank()) return
        when (value) {
            is JSONObject -> to.put(key, JSONObject(value.toString()))
            is JSONArray -> to.put(key, JSONArray(value.toString()))
            else -> to.put(key, value)
        }
    }

    private fun headersArray(obj: JSONObject?): JSONArray {
        val out = JSONArray()
        if (obj == null) return out
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            out.put(JSONObject().put("name", key).put("value", obj.optString(key, "")))
        }
        return out
    }

    private fun rawHeaders(raw: String): JSONArray {
        val out = JSONArray()
        raw.lines().forEach { line ->
            val separator = line.indexOf(':')
            if (separator > 0) {
                out.put(
                    JSONObject()
                        .put("name", line.substring(0, separator).trim())
                        .put("value", line.substring(separator + 1).trim())
                )
            }
        }
        return out
    }

    private fun queryArray(url: String): JSONArray {
        val out = JSONArray()
        val query = try { URL(url).query } catch (_: Exception) { null } ?: return out
        query.split('&').filter { it.isNotEmpty() }.forEach { item ->
            val separator = item.indexOf('=')
            if (separator >= 0) {
                out.put(JSONObject().put("name", item.substring(0, separator)).put("value", item.substring(separator + 1)))
            } else {
                out.put(JSONObject().put("name", item).put("value", ""))
            }
        }
        return out
    }

    private fun status(value: String, reason: String): JSONObject =
        JSONObject().put("status", value).put("reason", reason)

    private fun isoUtc(ms: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(ms))
    }
}
