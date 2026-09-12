package ru.evrasia.research

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.EditText
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

internal class WebNavigationController(
    private val activity: AppCompatActivity,
    private val web: WebView,
    private val address: EditText,
    private val record: (JSONObject) -> Unit
) {
    fun normalizeUrl(raw: String): String {
        val value = raw.trim()
        if (value.isBlank()) return "https://evrasia.rest/"
        return if (value.startsWith("http://") || value.startsWith("https://")) value else "https://$value"
    }

    fun navigate(raw: String) {
        val url = normalizeUrl(raw)
        address.setText(url)
        web.loadUrl(url)
    }


    fun handleExternalUrl(raw: String): Boolean {
        val uri = try { Uri.parse(raw) } catch (_: Exception) { return false }
        val scheme = uri.scheme?.lowercase().orEmpty()
        if (scheme in setOf("http", "https", "about", "javascript")) return false

        val event = JSONObject()
            .put("source", "deep-link")
            .put("time", System.currentTimeMillis())
            .put("url", raw)
            .put("scheme", scheme)

        return try {
            val intent = if (scheme == "intent") {
                Intent.parseUri(raw, Intent.URI_INTENT_SCHEME)
            } else {
                Intent(Intent.ACTION_VIEW, uri)
            }
            event.put("intentAction", intent.action ?: "")
            event.put("targetPackage", intent.component?.packageName ?: "")
            activity.startActivity(intent)
            event.put("result", "launched")
            record(event)
            true
        } catch (e: ActivityNotFoundException) {
            event.put("result", "no_handler").put("error", e.toString())
            record(event)
            true
        } catch (e: Exception) {
            event.put("result", "error").put("error", e.toString())
            record(event)
            true
        }
    }

    fun openInActiveWindow(url: String) {
        activity.runOnUiThread { navigate(url) }
        record(JSONObject().put("source", "new-window").put("time", System.currentTimeMillis()).put("url", url).put("method", "GET"))
    }
}
