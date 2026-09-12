package ru.evrasia.research

import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

internal class WebCaptureController(
    private val activity: AppCompatActivity,
    private val web: WebView,
    private val archive: ResearchArchive,
    userAgent: String,
    private val record: (JSONObject) -> Unit,
    private val onChanged: () -> Unit,
    private val onSnapshot: () -> Unit
) {
    private val scriptChunks = ConcurrentHashMap<String, MutableMap<Int, String>>()
    private val artifactChunks = ConcurrentHashMap<String, MutableMap<Int, String>>()
    private val resourceCapture = WebResourceCapture(
        archive = archive,
        userAgent = userAgent,
        record = record,
        onChanged = onChanged
    )

    val bridge = Bridge()

    fun clearPending() {
        resourceCapture.clearPending()
        scriptChunks.clear()
        artifactChunks.clear()
    }

    fun updateUserAgent(userAgent: String) {
        resourceCapture.updateUserAgent(userAgent)
    }

    fun shutdown() {
        resourceCapture.shutdown()
    }

    fun shouldAutoCopyResource(url: String, headers: Map<String, String>): Boolean =
        resourceCapture.shouldAutoCopyResource(url, headers)

    fun captureResource(url: String, headers: Map<String, String>, copyMode: String) {
        resourceCapture.captureResource(url, headers, copyMode)
    }

    fun requestResourceCopy(url: String, headersJson: JSONObject?): Boolean =
        resourceCapture.requestResourceCopy(url, headersJson)

    fun ensureInstrumentation() {
        web.evaluateJavascript(WebResearchScripts.instrumentation(), null)
    }

    fun captureLightPageSnapshot() {
        val nativeCookies = CookieManager.getInstance().getCookie(web.url ?: "") ?: ""
        web.evaluateJavascript(WebResearchScripts.lightSnapshot(nativeCookies), null)
    }

    fun capturePageSnapshot() {
        val nativeCookies = CookieManager.getInstance().getCookie(web.url ?: "") ?: ""
        web.evaluateJavascript(WebResearchScripts.fullSnapshot(nativeCookies), null)
    }

    inner class Bridge {
        @JavascriptInterface fun record(json: String) {
            try {
                record(JSONObject(json))
            } catch (e: Exception) {
                record(CaptureWarning.create(
                    code = "bridge_record_parse_failed",
                    message = "A browser-side event could not be parsed and was omitted.",
                    stage = "js-bridge",
                    error = e.toString(),
                    details = JSONObject().put("payloadChars", json.length)
                ))
            }
        }

        @JavascriptInterface fun snapshot(json: String) {
            try {
                archive.updateSnapshot(JSONObject(json))
                onSnapshot()
            } catch (e: Exception) {
                record(CaptureWarning.create(
                    code = "snapshot_parse_failed",
                    message = "The page snapshot could not be parsed and was omitted.",
                    stage = "snapshot",
                    url = web.url ?: "",
                    error = e.toString(),
                    details = JSONObject().put("payloadChars", json.length)
                ))
            }
        }

        @JavascriptInterface fun externalScript(url: String) {
            if (url.isNotBlank()) resourceCapture.captureExternalScript(url, emptyMap())
        }

        @JavascriptInterface fun requestSnapshot() {
            activity.runOnUiThread { capturePageSnapshot() }
        }

        @JavascriptInterface fun scriptChunk(url: String, index: Int, total: Int, chunk: String) {
            collectChunk(url, index, total, chunk, true)
        }

        @JavascriptInterface fun artifactChunk(key: String, index: Int, total: Int, chunk: String) {
            collectChunk(key, index, total, chunk, false)
        }
    }

    private fun collectChunk(key: String, index: Int, total: Int, chunk: String, script: Boolean) {
        val stage = if (script) "script-chunk" else "artifact-chunk"
        if (total <= 0 || index !in 0 until total) {
            val message = "An invalid chunk index was received; the artifact cannot be reconstructed."
            if (script) archive.putScriptError(key, message)
            record(CaptureWarning.create(
                code = "chunk_invalid_index",
                message = message,
                stage = stage,
                artifact = key,
                details = JSONObject().put("index", index).put("total", total).put("chunkChars", chunk.length)
            ))
            return
        }
        try {
            val all = if (script) scriptChunks else artifactChunks
            val map = all.getOrPut(key) { ConcurrentHashMap() }
            map[index] = chunk
            if (map.size == total) {
                val out = StringBuilder()
                for (i in 0 until total) out.append(map[i] ?: "")
                if (script) {
                    archive.putScript(key, out.toString().toByteArray(Charsets.UTF_8))
                } else {
                    archive.putArtifact(key, out.toString().toByteArray(Charsets.UTF_8))
                }
                all.remove(key)
                onChanged()
            }
        } catch (e: Exception) {
            if (script) archive.putScriptError(key, e.toString())
            record(CaptureWarning.create(
                code = "chunk_assembly_failed",
                message = "A chunked browser artifact could not be reconstructed.",
                stage = stage,
                artifact = key,
                error = e.toString(),
                details = JSONObject().put("index", index).put("total", total).put("chunkChars", chunk.length)
            ))
        }
    }
}
