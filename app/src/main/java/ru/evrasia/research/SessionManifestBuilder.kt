package ru.evrasia.research

import org.json.JSONArray
import org.json.JSONObject

internal class SessionManifestBuilder(private val archive: ResearchArchive) {
    fun build(pageUrl: String): JSONObject {
        val sourceCounts = linkedMapOf<String, Int>()
        val rawWarnings = JSONArray()
        synchronized(archive) {
            for (index in 0 until archive.records.length()) {
                val record = archive.records.optJSONObject(index) ?: continue
                val source = record.optString("source", "").ifBlank { "unknown" }
                sourceCounts[source] = (sourceCounts[source] ?: 0) + 1
                if (source == "capture-warning") {
                    rawWarnings.put(
                        JSONObject()
                            .put("code", record.optString("code", "capture_warning"))
                            .put("message", record.optString("message", record.optString("error", "")))
                            .put("time", record.optLong("time", 0))
                            .put("url", record.optString("url", record.optString("page", "")))
                    )
                }
            }
        }

        val snapshot = try { JSONObject(archive.snapshot.toString()) } catch (_: Exception) { JSONObject() }
        val sourceCountsJson = JSONObject()
        sourceCounts.toSortedMap().forEach { (source, count) -> sourceCountsJson.put(source, count) }

        var redirectChains = 0
        var redirectHops = 0
        var redirectLimitReached = 0
        var resourceCopyFailures = 0
        archive.resourceMeta.values.forEach { meta ->
            val hops = meta.optInt("redirectCount", 0)
            if (hops > 0) redirectChains++
            redirectHops += hops
            if (meta.optBoolean("redirectLimitReached", false)) redirectLimitReached++
            if (meta.has("error")) resourceCopyFailures++
        }

        val metadataOnly = archive.resourceMeta.keys.count { !archive.resources.containsKey(it) }
        val indexedDbArtifacts = archive.extraArtifacts.keys.count { it.startsWith("indexeddb-") }
        val cacheStorageArtifacts = archive.extraArtifacts.keys.count { it.startsWith("cache-") }
        val scriptRedirectArtifacts = archive.extraArtifacts.keys.count { it.startsWith("script-redirect-") }

        val fullSnapshotCaptured = snapshot.optBoolean("fullSnapshot", false)
        val pageHtmlCaptured = snapshot.optString("html", "").isNotEmpty()
        val cacheSnapshotHasError = cacheSnapshotHasError(snapshot)
        val indexedDbSnapshotHasError = indexedDbSnapshotHasError(snapshot)

        val warnings = JSONArray()
        if (!fullSnapshotCaptured) {
            addWarning(warnings, "full_snapshot_missing", "Full page snapshot was not present when the ZIP was created.")
        }
        if (archive.scriptErrors.isNotEmpty()) {
            addWarning(warnings, "script_archive_errors", "One or more JavaScript files could not be archived.", archive.scriptErrors.size)
        }
        if (resourceCopyFailures > 0) {
            addWarning(warnings, "resource_copy_failures", "One or more derivative resource copies failed.", resourceCopyFailures)
        }
        if (redirectLimitReached > 0) {
            addWarning(warnings, "redirect_limit_reached", "A derivative resource redirect chain reached the configured hop limit.", redirectLimitReached)
        }
        if (cacheSnapshotHasError) {
            addWarning(warnings, "cache_storage_snapshot_error", "Cache Storage snapshot reported an error.")
        }
        if (indexedDbSnapshotHasError) {
            addWarning(warnings, "indexeddb_snapshot_error", "IndexedDB snapshot reported an error.")
        }
        for (index in 0 until rawWarnings.length()) warnings.put(rawWarnings.getJSONObject(index))

        val completeness = JSONObject()
            .put("snapshotMode", when {
                fullSnapshotCaptured -> "full"
                snapshot.optBoolean("lightweight", false) -> "light"
                snapshot.length() > 0 -> "partial"
                else -> "none"
            })
            .put("pageSnapshotCaptured", snapshot.length() > 0)
            .put("fullSnapshotCaptured", fullSnapshotCaptured)
            .put("pageHtmlCaptured", pageHtmlCaptured)
            .put("cookiesSnapshotCaptured", snapshot.has("cookie") || snapshot.has("nativeCookie"))
            .put("localStorageSnapshotCaptured", snapshot.has("localStorage"))
            .put("sessionStorageSnapshotCaptured", snapshot.has("sessionStorage"))
            .put("serviceWorkersSnapshotCaptured", snapshot.has("serviceWorkers"))
            .put("cacheStorageSnapshotCaptured", snapshot.has("cacheStorage"))
            .put("indexedDbSnapshotCaptured", snapshot.has("indexedDB"))
            .put("resourceTimingSnapshotCaptured", snapshot.has("resources"))

        val counters = JSONObject()
            .put("rawEvents", archive.records.length())
            .put("rawEventsBySource", sourceCountsJson)
            .put("scriptsArchived", archive.scripts.size)
            .put("scriptErrors", archive.scriptErrors.size)
            .put("resourcesArchived", archive.resources.size)
            .put("resourceMetadata", archive.resourceMeta.size)
            .put("resourceMetadataOnly", metadataOnly)
            .put("resourceCopyFailures", resourceCopyFailures)
            .put("derivativeRedirectChains", redirectChains)
            .put("derivativeRedirectHops", redirectHops)
            .put("redirectLimitReached", redirectLimitReached)
            .put("browserArtifacts", archive.extraArtifacts.size)
            .put("indexedDbArtifacts", indexedDbArtifacts)
            .put("cacheStorageArtifacts", cacheStorageArtifacts)
            .put("scriptRedirectArtifacts", scriptRedirectArtifacts)
            .put("warnings", warnings.length())

        val limits = JSONObject()
            .put("cacheRequestsPerCache", 250)
            .put("cacheTextBodyChars", 200000)
            .put("indexedDbValuesPerStore", 1000)
            .put("fullSnapshotElements", 10000)
            .put("lightSnapshotElements", 2500)
            .put("bridgeChunkChars", 100000)
            .put("derivativeRedirectHops", 10)

        return JSONObject()
            .put("schemaVersion", 1)
            .put("format", "web-research-session-manifest-v1")
            .put("exportedAt", System.currentTimeMillis())
            .put("page", pageUrl)
            .put("counters", counters)
            .put("completeness", completeness)
            .put("limits", limits)
            .put("warnings", warnings)
    }

    private fun addWarning(target: JSONArray, code: String, message: String, count: Int = 1) {
        target.put(
            JSONObject()
                .put("code", code)
                .put("message", message)
                .put("count", count)
        )
    }

    private fun cacheSnapshotHasError(snapshot: JSONObject): Boolean {
        val values = snapshot.optJSONArray("cacheStorage") ?: return false
        for (index in 0 until values.length()) {
            if (values.optString(index, "").startsWith("ERROR:")) return true
        }
        return false
    }

    private fun indexedDbSnapshotHasError(snapshot: JSONObject): Boolean {
        val values = snapshot.optJSONArray("indexedDB") ?: return false
        for (index in 0 until values.length()) {
            if (values.optJSONObject(index)?.has("error") == true) return true
        }
        return false
    }
}
