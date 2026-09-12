package ru.evrasia.research

import android.os.SystemClock
import org.json.JSONObject
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.abs

internal class ForensicEventEnricher {
    private data class RequestEvidence(
        val requestId: String,
        val method: String,
        val url: String,
        val startedAt: Long,
        var completedAt: Long,
        val trafficClass: String,
        val sources: MutableSet<String>
    )

    private var eventSequence = 0L
    private var requestSequence = 0L
    private var actionSequence = 0L
    private var mutationSequence = 0L
    private var redirectSequence = 0L
    private val recentRequests = ArrayDeque<RequestEvidence>()
    private val realtimeRequests = linkedMapOf<String, String>()
    private var recentActionId = ""
    private var recentActionTime = 0L
    private var recentCompletedRequestId = ""
    private var recentCompletedRequestTime = 0L

    fun enrich(record: JSONObject): JSONObject {
        val now = System.currentTimeMillis()
        val time = record.optLong("time", now).takeIf { it > 0L } ?: now
        record.put("time", time)
        if (!record.has("timestamp")) record.put("timestamp", isoTimestamp(time))
        if (!record.has("elapsedRealtimeMs")) record.put("elapsedRealtimeMs", elapsedRealtimeMs())
        if (!record.has("eventId")) record.put("eventId", nextId("evt", ++eventSequence))

        val source = record.optString("source", "").ifBlank { "unknown" }
        when (source) {
            "user-action" -> enrichAction(record, time)
            "dom-mutation", "shadow-root", "custom-element" -> enrichMutation(record, time)
        }

        if (isRequestEvidence(source, record)) enrichRequest(record, source, time)
        return record
    }

    fun reset() {
        eventSequence = 0L
        requestSequence = 0L
        actionSequence = 0L
        mutationSequence = 0L
        redirectSequence = 0L
        recentRequests.clear()
        realtimeRequests.clear()
        recentActionId = ""
        recentActionTime = 0L
        recentCompletedRequestId = ""
        recentCompletedRequestTime = 0L
    }

    private fun enrichAction(record: JSONObject, time: Long) {
        val id = record.optString("actionId", "").ifBlank { nextId("action", ++actionSequence) }
        record.put("actionId", id)
        recentActionId = id
        recentActionTime = time
    }

    private fun enrichMutation(record: JSONObject, time: Long) {
        val id = record.optString("mutationId", "").ifBlank { nextId("dom", ++mutationSequence) }
        record.put("mutationId", id)
        if (recentActionId.isNotBlank() && time - recentActionTime in 0..5000L) {
            record.put("relatedActionId", recentActionId)
        }
        if (recentCompletedRequestId.isNotBlank() && time - recentCompletedRequestTime in 0..3000L) {
            record.put("relatedRequestId", recentCompletedRequestId)
        }
    }

    private fun enrichRequest(record: JSONObject, source: String, time: Long) {
        val requestSource = requestSource(source, record)
        val trafficClass = when (requestSource) {
            "recorder_derivative" -> "derivative"
            "manual_replay" -> "manual"
            else -> "browser"
        }
        record.put("requestSource", requestSource)
        record.put("trafficClass", trafficClass)

        val method = record.optString("method", defaultMethod(source)).ifBlank { defaultMethod(source) }.uppercase(Locale.US)
        val url = comparableUrl(record.optString("url", record.optString("page", "")))
        if (method.isNotBlank()) record.put("method", method)

        val realtimeKey = realtimeKey(source, url)
        var requestId = record.optString("requestId", "")
        if (requestId.isBlank() && realtimeKey != null && source !in setOf("websocket-open", "sse-open")) {
            requestId = realtimeRequests[realtimeKey].orEmpty()
        }

        val derivative = trafficClass != "browser"
        if (requestId.isBlank() && !derivative) {
            val candidate = findBrowserMatch(method, url, time, source)
            if (candidate != null) {
                requestId = candidate.requestId
                candidate.sources.add(source)
                candidate.completedAt = maxOf(candidate.completedAt, completionTime(record, time))
            }
        }

        if (requestId.isBlank()) {
            requestId = nextId("req", ++requestSequence)
            recentRequests.addLast(
                RequestEvidence(
                    requestId = requestId,
                    method = method,
                    url = url,
                    startedAt = time,
                    completedAt = completionTime(record, time),
                    trafficClass = trafficClass,
                    sources = mutableSetOf(source)
                )
            )
        }
        record.put("requestId", requestId)

        if (realtimeKey != null && source in setOf("websocket-open", "sse-open")) {
            realtimeRequests[realtimeKey] = requestId
        }

        if (derivative && !record.has("derivedFromRequestId")) {
            val origin = findDerivativeOrigin(method, url, time)
            if (origin != null) record.put("derivedFromRequestId", origin.requestId)
        }

        if (recentActionId.isNotBlank() && time - recentActionTime in 0..3000L && trafficClass == "browser") {
            record.put("relatedActionId", recentActionId)
        }

        val end = completionTime(record, time)
        if (hasCompletionEvidence(record, source)) {
            recentCompletedRequestId = requestId
            recentCompletedRequestTime = end
        }

        if ((record.optJSONArray("redirectChain")?.length() ?: 0) > 0) {
            record.put("redirectChainId", nextId("redirect", ++redirectSequence))
        } else if (record.optBoolean("redirected", false) && trafficClass == "browser") {
            record.put("redirectChainStatus", "partial_browser_api_does_not_expose_hops")
        }

        trim(time)
    }

    private fun findBrowserMatch(method: String, url: String, time: Long, source: String): RequestEvidence? {
        if (url.isBlank()) return null
        var best: RequestEvidence? = null
        var bestDelta = Long.MAX_VALUE
        val iterator = recentRequests.descendingIterator()
        while (iterator.hasNext()) {
            val candidate = iterator.next()
            if (candidate.trafficClass != "browser") continue
            if (source in candidate.sources) continue
            if (candidate.method != method && source != "navigation") continue
            if (candidate.url != url) continue
            val window = if (source == "navigation") 60000L else 5000L
            val delta = abs(time - candidate.startedAt)
            if (delta <= window && delta < bestDelta) {
                best = candidate
                bestDelta = delta
            }
        }
        return best
    }

    private fun findDerivativeOrigin(method: String, url: String, time: Long): RequestEvidence? {
        val iterator = recentRequests.descendingIterator()
        while (iterator.hasNext()) {
            val candidate = iterator.next()
            if (candidate.trafficClass != "browser") continue
            if (candidate.url != url) continue
            if (method.isNotBlank() && candidate.method != method && candidate.method != "GET") continue
            if (abs(time - candidate.startedAt) <= 30000L) return candidate
        }
        return null
    }

    private fun trim(now: Long) {
        while (recentRequests.isNotEmpty() && now - recentRequests.first().startedAt > 120000L) {
            recentRequests.removeFirst()
        }
        while (recentRequests.size > 512) recentRequests.removeFirst()
        if (realtimeRequests.size > 128) {
            val remove = realtimeRequests.keys.take(realtimeRequests.size - 128)
            remove.forEach(realtimeRequests::remove)
        }
    }

    private fun requestSource(source: String, record: JSONObject): String = when {
        source == "fetch" -> "fetch"
        source == "xhr" -> "xhr"
        source == "webview" && record.optBoolean("isForMainFrame", false) -> "webview_navigation"
        source == "webview" -> "resource"
        source in setOf("navigation", "new-window", "webview-event") -> "webview_navigation"
        source.startsWith("websocket-") -> "websocket"
        source.startsWith("sse-") -> "eventsource"
        source in setOf("resource-copy", "script-archive") -> "recorder_derivative"
        source in setOf("replay", "manual-replay") -> "manual_replay"
        source in setOf("resource-timing", "navigation-timing", "performance") -> "resource"
        else -> "unknown"
    }

    private fun isRequestEvidence(source: String, record: JSONObject): Boolean {
        if (source in setOf(
                "fetch", "xhr", "webview", "navigation", "new-window", "resource-copy", "script-archive",
                "replay", "manual-replay", "resource-timing", "navigation-timing"
            )) return record.has("url") || record.has("page")
        if (source.startsWith("websocket-") || source.startsWith("sse-")) return record.has("url")
        return false
    }

    private fun defaultMethod(source: String): String = when {
        source.startsWith("websocket-") -> "WS"
        source.startsWith("sse-") -> "SSE"
        else -> "GET"
    }

    private fun realtimeKey(source: String, url: String): String? = when {
        source.startsWith("websocket-") -> "ws|$url"
        source.startsWith("sse-") -> "sse|$url"
        else -> null
    }

    private fun completionTime(record: JSONObject, start: Long): Long =
        start + record.optDouble("duration", 0.0).coerceAtLeast(0.0).toLong()

    private fun hasCompletionEvidence(record: JSONObject, source: String): Boolean =
        record.has("status") || record.has("responseBody") || record.has("error") ||
            source in setOf("navigation", "websocket-state", "sse-state")

    private fun comparableUrl(value: String): String {
        if (value.isBlank()) return ""
        return try {
            val uri = URI(value)
            URI(
                uri.scheme?.lowercase(Locale.US),
                uri.userInfo,
                uri.host?.lowercase(Locale.US),
                uri.port,
                uri.path.ifBlank { "/" },
                uri.query,
                null
            ).toString()
        } catch (_: Exception) {
            value.substringBefore('#')
        }
    }

    private fun isoTimestamp(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private fun elapsedRealtimeMs(): Long =
        try {
            SystemClock.elapsedRealtime()
        } catch (_: Throwable) {
            System.nanoTime() / 1_000_000L
        }

    private fun nextId(prefix: String, value: Long): String = "$prefix-${value.toString().padStart(6, '0')}"
}
