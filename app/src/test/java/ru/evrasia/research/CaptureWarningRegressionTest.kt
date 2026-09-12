package ru.evrasia.research

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureWarningRegressionTest {
    @Test
    fun warningSchemaKeepsOmissionContext() {
        val warning = CaptureWarning.create(
            code = "test_omission",
            message = "Test artifact was omitted.",
            stage = "test",
            url = "https://example.test/app",
            artifact = "large.json",
            error = "too large",
            details = JSONObject().put("omitted", 10)
        )
        assertEquals("capture-warning", warning.getString("source"))
        assertEquals("test_omission", warning.getString("code"))
        assertEquals("test", warning.getString("stage"))
        assertEquals("large.json", warning.getString("artifact"))
        assertEquals(10, warning.getJSONObject("details").getInt("omitted"))
    }

    @Test
    fun manifestAggregatesRawWarningsWithoutLosingDetails() {
        val archive = ResearchArchive()
        archive.records.put(
            CaptureWarning.create(
                code = "fixture_omission",
                message = "Fixture artifact was partially omitted.",
                stage = "fixture",
                url = "https://example.test/app",
                artifact = "fixture-large.json",
                details = JSONObject().put("total", 1200).put("captured", 1000).put("omitted", 200)
            )
        )
        val manifest = SessionManifestBuilder(archive).build("https://example.test/app")
        val counters = manifest.getJSONObject("counters")
        assertEquals(1, counters.getInt("captureWarnings"))
        assertEquals(1, counters.getJSONObject("captureWarningsByCode").getInt("fixture_omission"))
        val warnings = manifest.getJSONArray("warnings")
        val warning = (0 until warnings.length())
            .map { warnings.getJSONObject(it) }
            .first { it.getString("code") == "fixture_omission" }
        assertEquals("fixture", warning.getString("stage"))
        assertEquals(200, warning.getJSONObject("details").getInt("omitted"))
    }

    @Test
    fun fullSnapshotDeclaresWarningsForConfiguredOmissionLimits() {
        val script = WebResearchScripts.fullSnapshot("")
        assertTrue(script.contains("cache_entries_truncated"))
        assertTrue(script.contains("cache_body_truncated"))
        assertTrue(script.contains("indexeddb_values_truncated"))
        assertTrue(script.contains("dom_elements_truncated"))
        assertTrue(script.contains("snapshot_delivery_failed"))
    }
}
