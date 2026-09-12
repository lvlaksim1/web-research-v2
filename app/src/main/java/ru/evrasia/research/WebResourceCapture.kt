package ru.evrasia.research

import android.webkit.CookieManager
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

internal class WebResourceCapture(
    private val archive: ResearchArchive,
    userAgent: String,
    private val record: (JSONObject) -> Unit,
    private val onChanged: () -> Unit
) {
    private val downloadingScripts = ConcurrentHashMap.newKeySet<String>()
    private val downloadingResources = ConcurrentHashMap.newKeySet<String>()
    private val executor = Executors.newFixedThreadPool(2)
    @Volatile private var currentUserAgent = userAgent

    private data class FollowResult(
        val connection: HttpURLConnection,
        val finalUrl: String,
        val redirectChain: JSONArray,
        val redirectLimitReached: Boolean
    )

    fun clearPending() {
        downloadingScripts.clear()
        downloadingResources.clear()
    }

    fun updateUserAgent(userAgent: String) {
        currentUserAgent = userAgent
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun looksLikeJs(url: String): Boolean {
        val clean = url.substringBefore('#').substringBefore('?').lowercase(Locale.US)
        return clean.endsWith(".js") || clean.endsWith(".mjs")
    }

    private fun headerValue(headers: Map<String, String>, name: String): String =
        headers.entries.firstOrNull { it.key.equals(name, true) }?.value.orEmpty()

    fun shouldAutoCopyResource(url: String, headers: Map<String, String>): Boolean {
        val clean = url.substringBefore('#').substringBefore('?').lowercase(Locale.US)
        val staticExt = listOf(".js", ".mjs", ".css", ".map", ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".ico", ".woff", ".woff2", ".ttf", ".otf")
        if (staticExt.any { clean.endsWith(it) }) return true
        val destination = headerValue(headers, "Sec-Fetch-Dest").lowercase(Locale.US)
        if (destination in setOf("script", "style", "image", "font")) return true
        val accept = headerValue(headers, "Accept").lowercase(Locale.US)
        return accept.contains("image/") || accept.contains("font/") || accept.contains("text/css") || accept.contains("javascript")
    }

    fun requestResourceCopy(url: String, headersJson: JSONObject?): Boolean {
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return false
        val headers = linkedMapOf<String, String>()
        if (headersJson != null) {
            val keys = headersJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                headers[key] = headersJson.optString(key, "")
            }
        }
        captureResource(url, headers, "manual-fallback")
        return true
    }

    private fun openConnection(url: String, headers: Map<String, String>): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 15000
        connection.readTimeout = 45000
        connection.requestMethod = "GET"
        headers.forEach { (key, value) ->
            if (!key.equals("Host", true) && !key.equals("Content-Length", true) && !key.equals("Cookie", true)) {
                try { connection.setRequestProperty(key, value) } catch (_: Exception) {}
            }
        }
        CookieManager.getInstance().getCookie(url)?.let { connection.setRequestProperty("Cookie", it) }
        connection.setRequestProperty("User-Agent", currentUserAgent)
        return connection
    }

    private fun responseHeaders(connection: HttpURLConnection): JSONObject {
        val out = JSONObject()
        connection.headerFields.filterKeys { it != null }.forEach { (key, values) ->
            out.put(key, values.joinToString(", "))
        }
        return out
    }

    private fun isRedirect(status: Int): Boolean = status in setOf(301, 302, 303, 307, 308)

    private fun followRedirects(url: String, headers: Map<String, String>, maxRedirects: Int = 10): FollowResult {
        var current = url
        var redirectCount = 0
        val chain = JSONArray()
        while (true) {
            val connection = openConnection(current, headersForHop(headers, url, current))
            val hopStarted = System.currentTimeMillis()
            val status = connection.responseCode
            val location = connection.getHeaderField("Location").orEmpty()
            if (!isRedirect(status) || location.isBlank()) {
                return FollowResult(connection, current, chain, false)
            }

            val next = try { URL(URL(current), location).toString() } catch (_: Exception) { location }
            chain.put(
                JSONObject()
                    .put("index", redirectCount)
                    .put("url", current)
                    .put("status", status)
                    .put("statusText", connection.responseMessage ?: "")
                    .put("location", location)
                    .put("resolvedLocation", next)
                    .put("responseHeaders", responseHeaders(connection))
                    .put("duration", System.currentTimeMillis() - hopStarted)
            )
            redirectCount++
            if (redirectCount >= maxRedirects) {
                return FollowResult(connection, current, chain, true)
            }
            connection.disconnect()
            current = next
        }
    }

    private fun headersForHop(headers: Map<String, String>, originalUrl: String, currentUrl: String): Map<String, String> {
        if (originalUrl == currentUrl) return headers
        val originalHost = try { URL(originalUrl).host } catch (_: Exception) { "" }
        val currentHost = try { URL(currentUrl).host } catch (_: Exception) { "" }
        if (originalHost.equals(currentHost, true)) return headers
        return headers.filterKeys {
            !it.equals("Authorization", true) &&
                !it.equals("Proxy-Authorization", true) &&
                !it.equals("Cookie", true)
        }
    }

    fun captureResource(url: String, headers: Map<String, String>, copyMode: String) {
        if (archive.resources.containsKey(url) || !downloadingResources.add(url)) return
        executor.execute {
            try {
                val started = System.currentTimeMillis()
                val followed = followRedirects(url, headers)
                val connection = followed.connection
                val status = connection.responseCode
                val bytes = (if (status in 200..399) connection.inputStream else connection.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
                val responseHeaders = responseHeaders(connection)
                val finalUrl = connection.url.toString()
                val contentType = connection.contentType ?: ""
                val resourceMeta = JSONObject()
                    .put("status", status)
                    .put("contentType", contentType)
                    .put("finalUrl", finalUrl)
                    .put("responseHeaders", responseHeaders)
                    .put("copyMode", copyMode)
                    .put("redirected", followed.redirectChain.length() > 0)
                    .put("redirectCount", followed.redirectChain.length())
                    .put("redirectChain", followed.redirectChain)
                    .put("redirectLimitReached", followed.redirectLimitReached)
                    .put("evidenceType", "derivative-resource-copy")
                archive.putResource(url, bytes, resourceMeta)
                if (looksLikeJs(url) || contentType.contains("javascript", true)) archive.putScript(url, bytes)
                if (status !in 200..399) {
                    record(CaptureWarning.create(
                        code = "resource_copy_http_error",
                        message = "A derivative resource copy returned an HTTP error; the response evidence was kept.",
                        stage = "resource-copy",
                        url = url,
                        details = JSONObject().put("status", status).put("finalUrl", finalUrl).put("copyMode", copyMode)
                    ))
                }
                if (followed.redirectLimitReached) {
                    record(CaptureWarning.create(
                        code = "resource_redirect_limit_reached",
                        message = "A derivative resource redirect chain reached the configured hop limit.",
                        stage = "resource-copy",
                        url = url,
                        details = JSONObject().put("redirectCount", followed.redirectChain.length()).put("copyMode", copyMode)
                    ))
                }
                record(
                    JSONObject()
                        .put("source", "resource-copy")
                        .put("copyMode", copyMode)
                        .put("evidenceType", "derivative-resource-copy")
                        .put("time", started)
                        .put("duration", System.currentTimeMillis() - started)
                        .put("method", "GET")
                        .put("url", url)
                        .put("finalUrl", finalUrl)
                        .put("status", status)
                        .put("statusText", connection.responseMessage ?: "")
                        .put("responseHeaders", responseHeaders)
                        .put("mimeType", contentType)
                        .put("responseSize", bytes.size)
                        .put("redirected", followed.redirectChain.length() > 0)
                        .put("redirectCount", followed.redirectChain.length())
                        .put("redirectChain", followed.redirectChain)
                        .put("redirectLimitReached", followed.redirectLimitReached)
                        .put("redirectURL", if (finalUrl != url) finalUrl else "")
                )
                connection.disconnect()
            } catch (e: Exception) {
                archive.putResourceMeta(url, JSONObject().put("error", e.toString()).put("copyMode", copyMode))
                record(CaptureWarning.create(
                    code = "resource_copy_failed",
                    message = "A derivative resource copy failed and its body could not be archived.",
                    stage = "resource-copy",
                    url = url,
                    error = e.toString(),
                    details = JSONObject().put("copyMode", copyMode)
                ))
                record(
                    JSONObject()
                        .put("source", "resource-copy")
                        .put("copyMode", copyMode)
                        .put("evidenceType", "derivative-resource-copy")
                        .put("time", System.currentTimeMillis())
                        .put("method", "GET")
                        .put("url", url)
                        .put("error", e.toString())
                )
            } finally {
                downloadingResources.remove(url)
                onChanged()
            }
        }
    }

    fun captureExternalScript(url: String, headers: Map<String, String>) {
        if (url.startsWith("blob:") || url.startsWith("data:") || archive.scripts.containsKey(url) || !downloadingScripts.add(url)) return
        executor.execute {
            try {
                val followed = followRedirects(url, headers)
                val connection = followed.connection
                val status = connection.responseCode
                val bytes = (if (status in 200..399) connection.inputStream else connection.errorStream)?.use { it.readBytes() }
                if (bytes != null) {
                    archive.putScript(url, bytes)
                } else {
                    val message = "HTTP $status: empty body"
                    archive.putScriptError(url, message)
                    record(CaptureWarning.create(
                        code = "script_archive_empty_body",
                        message = "An external JavaScript response had no body to archive.",
                        stage = "script-archive",
                        url = url,
                        error = message,
                        details = JSONObject().put("status", status).put("finalUrl", followed.finalUrl)
                    ))
                }
                if (status !in 200..399) {
                    record(CaptureWarning.create(
                        code = "script_archive_http_error",
                        message = "An external JavaScript copy returned an HTTP error; available response bytes were kept.",
                        stage = "script-archive",
                        url = url,
                        details = JSONObject().put("status", status).put("finalUrl", followed.finalUrl)
                    ))
                }
                if (followed.redirectLimitReached) {
                    record(CaptureWarning.create(
                        code = "script_redirect_limit_reached",
                        message = "An external JavaScript redirect chain reached the configured hop limit.",
                        stage = "script-archive",
                        url = url,
                        details = JSONObject().put("redirectCount", followed.redirectChain.length())
                    ))
                }
                if (followed.redirectChain.length() > 0) {
                    archive.putArtifact(
                        "script-redirect-${url.hashCode().toUInt().toString(16)}.json",
                        JSONObject()
                            .put("url", url)
                            .put("finalUrl", followed.finalUrl)
                            .put("redirectChain", followed.redirectChain)
                            .put("redirectLimitReached", followed.redirectLimitReached)
                            .toString(2)
                            .toByteArray(Charsets.UTF_8)
                    )
                }
                connection.disconnect()
            } catch (e: Exception) {
                archive.putScriptError(url, e.toString())
                record(CaptureWarning.create(
                    code = "script_archive_failed",
                    message = "An external JavaScript file could not be archived.",
                    stage = "script-archive",
                    url = url,
                    error = e.toString()
                ))
            } finally {
                downloadingScripts.remove(url)
                onChanged()
            }
        }
    }
}
