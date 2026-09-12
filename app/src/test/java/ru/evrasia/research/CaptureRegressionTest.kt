package ru.evrasia.research

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class CaptureRegressionTest {
    @Before
    fun resetStore() {
        NetworkDebugStore.recording = true
        NetworkDebugStore.clear()
    }

    @Test
    fun rawRecordIsNotMutatedByDebuggerNormalization() {
        val raw = JSONArray()
        val record = JSONObject()
            .put("source", "webview")
            .put("time", 1000L)
            .put("method", "GET")
            .put("url", "https://example.test/api/items")
            .put("headers", JSONObject().put("X-Legacy", "legacy-value"))
        val original = record.toString()

        NetworkRecordPipeline.appendRawAndDebug(raw, record)

        assertEquals(1, raw.length())
        assertEquals(original, raw.getJSONObject(0).toString())
        assertFalse(raw.getJSONObject(0).has("requestHeaders"))
        val debug = NetworkDebugStore.snapshot().single()
        assertEquals("legacy-value", debug.getJSONObject("requestHeaders").getString("X-Legacy"))
    }

    @Test
    fun correlatedDebuggerCopyKeepsOneRequestAndCombinesEvidence() {
        val webview = JSONObject()
            .put("source", "webview")
            .put("time", 1000L)
            .put("method", "GET")
            .put("url", "https://example.test/api/items")
        val fetch = JSONObject()
            .put("source", "fetch")
            .put("time", 1050L)
            .put("method", "GET")
            .put("url", "https://example.test/api/items")
            .put("status", 200)
            .put("responseBody", "{\"ok\":true}")

        NetworkDebugStore.add(webview)
        NetworkDebugStore.add(fetch)

        val snapshot = NetworkDebugStore.snapshot()
        assertEquals(1, snapshot.size)
        val merged = snapshot.single()
        assertEquals(200, merged.getInt("status"))
        assertEquals("{\"ok\":true}", merged.getString("responseBody"))
        val sources = merged.getJSONArray("capturedSources")
        assertTrue((0 until sources.length()).map { sources.getString(it) }.containsAll(listOf("webview", "fetch")))
    }

    @Test
    fun projectionCollapsesRealtimeAndPreservesActions() {
        val fixture = fixture()
        val all = fixture.getJSONArray("events").objects().reversed()
        val result = NetworkDebuggerProjection.build(
            allItems = all,
            mergeMode = false,
            domain = "Все домены",
            type = "ALL",
            method = "ALL",
            query = "",
            methodFilters = listOf("ALL", "GET", "POST", "WS", "SSE", "ACTION", "OTHER")
        )

        val ws = result.rows.single { it.optBoolean("_realtimeSession") && it.optString("_realtimeProtocol") == "WS" }
        val sse = result.rows.single { it.optBoolean("_realtimeSession") && it.optString("_realtimeProtocol") == "SSE" }
        assertEquals(3, ws.getInt("_sessionCount"))
        assertEquals(2, sse.getInt("_sessionCount"))
        assertTrue(result.rows.any { it.optString("source") == "user-action" })
        assertTrue(result.counterText.contains("действий"))
    }

    @Test
    fun changedDetectionMarksNewestDifferentResponse() {
        val older = JSONObject()
            .put("source", "fetch")
            .put("_storeId", 10L)
            .put("time", 1000L)
            .put("method", "GET")
            .put("url", "https://example.test/api/state")
            .put("status", 200)
            .put("mimeType", "application/json")
            .put("responseBody", "{\"value\":1}")
        val newer = JSONObject(older.toString())
            .put("_storeId", 11L)
            .put("time", 2000L)
            .put("responseBody", "{\"value\":2}")

        val result = NetworkDebuggerProjection.build(
            allItems = listOf(newer, older),
            mergeMode = false,
            domain = "Все домены",
            type = "ALL",
            method = "ALL",
            query = "",
            methodFilters = listOf("ALL", "GET", "OTHER")
        )

        assertTrue(result.changedIds.contains(11L))
        assertFalse(result.changedIds.contains(10L))
    }

    @Test
    fun cookieSupportSelectsExactCurrentOrigin() {
        val history = listOf(
            JSONObject()
                .put("time", 1000L)
                .put("action", "OBSERVED")
                .put("name", "session")
                .put("value", "old")
                .put("confidence", "UNKNOWN")
                .put("origin", "PREEXISTING_OR_UNKNOWN"),
            JSONObject()
                .put("time", 2000L)
                .put("action", "SET")
                .put("name", "session")
                .put("value", "current")
                .put("confidence", "EXACT")
                .put("origin", "HTTP_RESPONSE")
        )

        val current = CookieTraceSupport.currentOrigin(history, "current")
        val regeneration = CookieTraceSupport.regenerationEvent(history, "current")
        assertNotNull(current)
        assertEquals("HTTP_RESPONSE", current!!.getString("origin"))
        assertEquals("HTTP_RESPONSE", regeneration!!.getString("origin"))
        assertEquals("abc", CookieTraceSupport.parseCookieHeader("a=abc; b=2")["a"])
    }

    @Test
    fun archiveExporterKeepsRawEventsAndExpectedZipStructure() {
        val fixture = fixture()
        val archive = ResearchArchive()
        val events = fixture.getJSONArray("events")
        for (index in 0 until events.length()) {
            archive.records.put(JSONObject(events.getJSONObject(index).toString()))
        }
        archive.updateSnapshot(JSONObject().put("html", "<html><body>fixture</body></html>"))
        archive.putScript("https://example.test/app.js", "console.log('fixture')".toByteArray())
        archive.putResource(
            "https://example.test/logo.png",
            byteArrayOf(1, 2, 3, 4),
            JSONObject().put("contentType", "image/png")
        )
        archive.putArtifact("fixture/meta.json", "{\"fixture\":true}".toByteArray())

        val output = ByteArrayOutputStream()
        ResearchArchiveExporter(archive).writeZip(output, fixture.getString("page"))
        val entries = unzip(output.toByteArray())

        val required = setOf(
            "session-manifest.json",
            "raw-events.json",
            "network.har",
            "api-summary.json",
            "actions.json",
            "dom-mutations.json",
            "realtime.json",
            "performance.json",
            "page-snapshot.json",
            "page.html",
            "js/manifest.json",
            "resources/manifest.json",
            "browser/fixture/meta.json"
        )
        assertTrue(entries.keys.containsAll(required))
        val raw = JSONObject(entries.getValue("raw-events.json").toString(Charsets.UTF_8))
        assertEquals(events.length(), raw.getJSONArray("records").length())
        val har = JSONObject(entries.getValue("network.har").toString(Charsets.UTF_8))
        assertTrue(har.getJSONObject("log").getJSONArray("entries").length() >= 3)
        val manifest = JSONObject(entries.getValue("session-manifest.json").toString(Charsets.UTF_8))
        assertEquals(1, manifest.getInt("schemaVersion"))
        assertEquals(events.length(), manifest.getJSONObject("counters").getInt("rawEvents"))
        assertEquals(1, manifest.getJSONObject("counters").getInt("scriptsArchived"))
        assertEquals(1, manifest.getJSONObject("counters").getInt("resourcesArchived"))
        assertEquals(1, manifest.getJSONObject("counters").getInt("browserArtifacts"))
        assertTrue(manifest.getJSONObject("completeness").getBoolean("pageHtmlCaptured"))
        assertFalse(manifest.getJSONObject("completeness").getBoolean("fullSnapshotCaptured"))
        assertTrue(
            manifest.getJSONArray("warnings").objects()
                .any { it.getString("code") == "full_snapshot_missing" }
        )
    }

    private fun fixture(): JSONObject {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("session-fixture.json"))
        return stream.bufferedReader().use { JSONObject(it.readText()) }
    }

    private fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).map { getJSONObject(it) }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val buffer = ByteArrayOutputStream()
                zip.copyTo(buffer)
                out[entry.name] = buffer.toByteArray()
                zip.closeEntry()
            }
        }
        return out
    }
}
