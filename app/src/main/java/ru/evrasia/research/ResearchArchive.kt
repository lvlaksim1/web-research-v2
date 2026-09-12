package ru.evrasia.research

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ResearchArchive {
    val records = JSONArray()
    private val forensicEnricher = ForensicEventEnricher()
    val scripts = ConcurrentHashMap<String, ByteArray>()
    val scriptErrors = ConcurrentHashMap<String, String>()
    val resources = ConcurrentHashMap<String, ByteArray>()
    val resourceMeta = ConcurrentHashMap<String, JSONObject>()
    val extraArtifacts = ConcurrentHashMap<String, ByteArray>()
    val snapshots = ConcurrentHashMap<Long, JSONObject>()
    private val recordCapturedAt = mutableListOf<Long>()
    private val scriptCapturedAt = ConcurrentHashMap<String, Long>()
    private val scriptErrorCapturedAt = ConcurrentHashMap<String, Long>()
    private val resourceCapturedAt = ConcurrentHashMap<String, Long>()
    private val resourceMetaCapturedAt = ConcurrentHashMap<String, Long>()
    private val artifactCapturedAt = ConcurrentHashMap<String, Long>()
    @Volatile private var snapshotCapturedAt = 0L
    @Volatile var snapshot = JSONObject()
    @Volatile internal var selectedSessionId: String = ""
    @Volatile internal var selectedStartedAt: Long = 0L
    @Volatile internal var selectedEndedAt: Long = 0L

    @Synchronized fun beginForensicSession(startedAt: Long): String {
        val id = UUID.randomUUID().toString()
        selectedSessionId = id
        selectedStartedAt = startedAt
        selectedEndedAt = 0L
        return id
    }

    @Synchronized fun endForensicSession(endedAt: Long) {
        selectedEndedAt = endedAt
    }

    @Synchronized fun addRecord(record: JSONObject): JSONObject {
        forensicEnricher.enrich(record)
        NetworkRecordPipeline.appendRawAndDebug(records, record)
        recordCapturedAt.add(System.currentTimeMillis())
        return record
    }

    fun putScript(url: String, bytes: ByteArray) {
        scriptCapturedAt[url] = System.currentTimeMillis()
        val previous = scripts.put(url, bytes)
        if (previous == null && isInlineScript(url)) {
            NetworkRecordPipeline.addDebuggerOnly(JSONObject()
                .put("source", "js-file")
                .put("time", System.currentTimeMillis())
                .put("method", "JS")
                .put("url", url)
                .put("mimeType", "application/javascript")
                .put("responseSize", bytes.size)
                .put("responseBody", try { bytes.toString(Charsets.UTF_8) } catch (_: Exception) { "[binary]" }))
        }
    }

    fun putScriptError(url: String, error: String) {
        scriptErrorCapturedAt[url] = System.currentTimeMillis()
        scriptErrors[url] = error
    }

    fun putResource(url: String, bytes: ByteArray, meta: JSONObject) {
        val capturedAt = System.currentTimeMillis()
        resourceCapturedAt[url] = capturedAt
        resourceMetaCapturedAt[url] = capturedAt
        resources[url] = bytes
        resourceMeta[url] = meta
    }

    fun putResourceMeta(url: String, meta: JSONObject) {
        resourceMetaCapturedAt[url] = System.currentTimeMillis()
        resourceMeta[url] = meta
    }

    fun putArtifact(key: String, bytes: ByteArray) {
        artifactCapturedAt[key] = System.currentTimeMillis()
        extraArtifacts[key] = bytes
    }

    fun updateSnapshot(value: JSONObject) {
        val capturedAt = System.currentTimeMillis()
        snapshotCapturedAt = capturedAt
        val copy = JSONObject(value.toString())
        snapshot = copy
        snapshots[capturedAt] = JSONObject(copy.toString())
        val sessionId = selectedSessionId
        if (sessionId.isNotBlank()) {
            val label = when {
                selectedEndedAt > 0L && capturedAt >= selectedEndedAt -> "after"
                snapshots.size == 1 -> "before"
                else -> "checkpoint"
            }
            putArtifact(
                "capture-session/$sessionId/checkpoints/$label-$capturedAt.json",
                copy.toString(2).toByteArray(Charsets.UTF_8)
            )
            val html = copy.optString("html", "")
            if (html.isNotEmpty()) {
                putArtifact(
                    "capture-session/$sessionId/dom/$label-$capturedAt.html",
                    html.toByteArray(Charsets.UTF_8)
                )
            }
            if (copy.has("cookie") || copy.has("nativeCookie")) {
                val cookies = JSONObject()
                    .put("capturedAt", capturedAt)
                    .put("page", copy.optString("url", ""))
                    .put("documentCookie", copy.optString("cookie", ""))
                    .put("nativeCookie", copy.optString("nativeCookie", ""))
                    .put("nativeCookieSource", "CookieManager")
                val cookiePath = if (label == "before" || label == "after") {
                    "capture-session/$sessionId/cookies/$label.json"
                } else {
                    "capture-session/$sessionId/cookies/checkpoints/$capturedAt.json"
                }
                putArtifact(cookiePath, cookies.toString(2).toByteArray(Charsets.UTF_8))
            }
            copy.optJSONObject("localStorage")?.let {
                putArtifact(
                    "capture-session/$sessionId/storage/$label/localStorage.json",
                    it.toString(2).toByteArray(Charsets.UTF_8)
                )
            }
            copy.optJSONObject("sessionStorage")?.let {
                putArtifact(
                    "capture-session/$sessionId/storage/$label/sessionStorage.json",
                    it.toString(2).toByteArray(Charsets.UTF_8)
                )
            }
            if (label == "after") {
                val before = snapshots.entries.minByOrNull { it.key }?.value
                if (before != null) {
                    val cookieDiff = cookieDiff(
                        before.optString("nativeCookie", before.optString("cookie", "")),
                        copy.optString("nativeCookie", copy.optString("cookie", ""))
                    )
                    putArtifact(
                        "capture-session/$sessionId/cookies/diffs/before-after.json",
                        cookieDiff.toString(2).toByteArray(Charsets.UTF_8)
                    )
                    val storageDiff = JSONObject()
                        .put(
                            "localStorage",
                            objectDiff(
                                before.optJSONObject("localStorage") ?: JSONObject(),
                                copy.optJSONObject("localStorage") ?: JSONObject()
                            )
                        )
                        .put(
                            "sessionStorage",
                            objectDiff(
                                before.optJSONObject("sessionStorage") ?: JSONObject(),
                                copy.optJSONObject("sessionStorage") ?: JSONObject()
                            )
                        )
                    putArtifact(
                        "capture-session/$sessionId/storage/diff.json",
                        storageDiff.toString(2).toByteArray(Charsets.UTF_8)
                    )
                }
            }
        }
        if (snapshots.size > 24) {
            snapshots.keys.sorted().take(snapshots.size - 24).forEach(snapshots::remove)
        }
    }

    @Synchronized fun snapshotWindow(startedAt: Long, endedAt: Long, sessionId: String = ""): ResearchArchive {
        val effectiveSessionId = sessionId.ifBlank { selectedSessionId }
        val out = ResearchArchive()
        out.selectedSessionId = effectiveSessionId
        out.selectedStartedAt = startedAt
        out.selectedEndedAt = endedAt
        for (index in 0 until records.length()) {
            val capturedAt = recordCapturedAt.getOrNull(index) ?: continue
            if (capturedAt in startedAt..endedAt) {
                records.optJSONObject(index)?.let { out.records.put(JSONObject(it.toString())) }
            }
        }

        scripts.forEach { (key, value) ->
            val capturedAt = scriptCapturedAt[key] ?: Long.MIN_VALUE
            if (capturedAt in startedAt..endedAt) out.scripts[key] = value.copyOf()
        }
        scriptErrors.forEach { (key, value) ->
            val capturedAt = scriptErrorCapturedAt[key] ?: Long.MIN_VALUE
            if (capturedAt in startedAt..endedAt) out.scriptErrors[key] = value
        }
        resources.forEach { (key, value) ->
            val capturedAt = resourceCapturedAt[key] ?: Long.MIN_VALUE
            if (capturedAt in startedAt..endedAt) out.resources[key] = value.copyOf()
        }
        resourceMeta.forEach { (key, value) ->
            val capturedAt = resourceMetaCapturedAt[key] ?: Long.MIN_VALUE
            if (capturedAt in startedAt..endedAt) out.resourceMeta[key] = JSONObject(value.toString())
        }
        extraArtifacts.forEach { (key, value) ->
            val capturedAt = artifactCapturedAt[key] ?: Long.MIN_VALUE
            val belongsToSession = effectiveSessionId.isNotBlank() && key.startsWith("capture-session/$effectiveSessionId/")
            if (capturedAt in startedAt..endedAt || belongsToSession) out.extraArtifacts[key] = value.copyOf()
        }
        snapshots.entries.sortedBy { it.key }.forEach { (capturedAt, value) ->
            if (capturedAt in startedAt..(endedAt + 1500L)) {
                out.snapshots[capturedAt] = JSONObject(value.toString())
            }
        }

        out.snapshot = try { JSONObject(snapshot.toString()) } catch (_: Exception) { JSONObject() }
        out.snapshotCapturedAt = snapshotCapturedAt
        return out
    }

    private fun isInlineScript(url: String): Boolean =
        (!url.startsWith("http://") && !url.startsWith("https://")) || url.contains("#inline-")

    private fun cookieDiff(beforeRaw: String, afterRaw: String): JSONObject {
        fun parse(raw: String): Map<String, String> {
            val out = linkedMapOf<String, String>()
            raw.split(';').forEach { part ->
                val clean = part.trim()
                if (clean.isBlank()) return@forEach
                val separator = clean.indexOf('=')
                if (separator <= 0) return@forEach
                out[clean.substring(0, separator).trim()] = clean.substring(separator + 1)
            }
            return out
        }

        val before = parse(beforeRaw)
        val after = parse(afterRaw)
        val added = JSONObject()
        val removed = JSONObject()
        val changed = JSONObject()

        after.forEach { (name, value) ->
            if (!before.containsKey(name)) added.put(name, value)
            else if (before[name] != value) {
                changed.put(
                    name,
                    JSONObject()
                        .put("before", before[name])
                        .put("after", value)
                )
            }
        }
        before.forEach { (name, value) ->
            if (!after.containsKey(name)) removed.put(name, value)
        }
        return JSONObject()
            .put("added", added)
            .put("removed", removed)
            .put("changed", changed)
    }

    private fun objectDiff(before: JSONObject, after: JSONObject): JSONObject {
        val beforeKeys = mutableSetOf<String>()
        val afterKeys = mutableSetOf<String>()
        before.keys().forEachRemaining(beforeKeys::add)
        after.keys().forEachRemaining(afterKeys::add)

        val added = JSONObject()
        val removed = JSONObject()
        val changed = JSONObject()

        (afterKeys - beforeKeys).sorted().forEach { key -> added.put(key, after.opt(key)) }
        (beforeKeys - afterKeys).sorted().forEach { key -> removed.put(key, before.opt(key)) }
        (beforeKeys intersect afterKeys).sorted().forEach { key ->
            val oldValue = before.opt(key)
            val newValue = after.opt(key)
            if (oldValue?.toString() != newValue?.toString()) {
                changed.put(
                    key,
                    JSONObject()
                        .put("before", oldValue ?: JSONObject.NULL)
                        .put("after", newValue ?: JSONObject.NULL)
                )
            }
        }
        return JSONObject()
            .put("added", added)
            .put("removed", removed)
            .put("changed", changed)
    }

    @Synchronized fun clear() {
        while (records.length() > 0) records.remove(records.length() - 1)
        scripts.clear()
        scriptErrors.clear()
        resources.clear()
        resourceMeta.clear()
        extraArtifacts.clear()
        snapshots.clear()
        recordCapturedAt.clear()
        scriptCapturedAt.clear()
        scriptErrorCapturedAt.clear()
        resourceCapturedAt.clear()
        resourceMetaCapturedAt.clear()
        artifactCapturedAt.clear()
        snapshotCapturedAt = 0L
        snapshot = JSONObject()
        selectedSessionId = ""
        selectedStartedAt = 0L
        selectedEndedAt = 0L
        forensicEnricher.reset()
        NetworkRecordPipeline.clearDebugger()
    }

}
