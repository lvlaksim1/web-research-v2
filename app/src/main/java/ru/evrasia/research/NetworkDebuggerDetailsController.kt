package ru.evrasia.research

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.view.Gravity
import android.webkit.CookieManager
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.util.IdentityHashMap

internal class NetworkDebuggerDetailsController(
    private val activity: AppCompatActivity,
    private val changedIds: Set<Long>
) {
    private val palette get() = WebUiTheme.palette(activity)
    private val bg get() = palette.background
    private val panel get() = palette.card
    private val panel2 get() = palette.address
    private val line get() = palette.divider
    private val accent get() = palette.accent
    private val textColor get() = palette.text
    private val muted get() = palette.secondary
    private val bad get() = palette.red
    private val cyan get() = palette.accent
    private val amber get() = palette.orange
    private val violet get() = palette.blue

    private val views = NetworkDebuggerDetailViews(activity)

    private val replayController by lazy {
        NetworkReplayController(activity, bg, panel2, line, textColor, muted)
    }

    fun show(event: JSONObject, query: String) {
        val url = event.optString("url", "")
        val requestCookies =
            if (url.startsWith("http")) CookieManager.getInstance().getCookie(url).orEmpty() else ""
        val responseHeaders = event.optJSONObject("responseHeaders")
        val mime = event.optString(
            "mimeType",
            NetworkDebuggerText.headerValue(responseHeaders, "Content-Type")
        ).substringBefore(';').trim()
        val responseBody = NetworkEventClassifier.responseBodyText(event)
        val requestHeadersList = NetworkDebuggerText.requestHeaderPairs(event)
        val responseHeadersList = NetworkDebuggerText.responseHeaderPairs(event)
        val bytes = NetworkRequestActions.responseBytes(activity, url)
        val binary =
            responseBody == "[binary]" ||
                responseBody == "[non-text response]" ||
                (
                    bytes != null &&
                        bytes.isNotEmpty() &&
                        NetworkDebuggerText.isBinaryPayload(mime, responseBody, bytes)
                    )
        val imageBitmap =
            if (binary && bytes != null) {
                try {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
        val originalTexts = IdentityHashMap<TextView, CharSequence>()
        var decoded = false
        var dialog: AlertDialog? = null

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(views.dp(10), views.dp(10), views.dp(10), views.dp(10))
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = views.rounded(panel, 14f, line)
            setPadding(views.dp(12), views.dp(10), views.dp(12), views.dp(10))
        }
        val titleRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val status = event.optInt("status", 0)
        titleRow.addView(
            views.compactIconButton(TechIconDrawable.Kind.CLOSE, "Закрыть") { dialog?.dismiss() },
            LinearLayout.LayoutParams(views.dp(48), views.dp(48)).apply { marginEnd = views.dp(7) }
        )
        titleRow.addView(
            TextView(activity).apply {
                text = buildString {
                    append(NetworkEventClassifier.methodOf(event))
                    if (status > 0) append("  ").append(status)
                    if (event.has("duration")) {
                        append("  ").append(
                            NetworkDebuggerText.formatDuration(event.optDouble("duration", 0.0))
                        )
                    }
                    if (event.has("responseSize")) {
                        append("  ").append(
                            NetworkDebuggerText.formatBytes(event.optLong("responseSize"))
                        )
                    }
                }
                setTextColor(if (status >= 400 || event.has("error")) bad else accent)
                textSize = 14f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        val responseKind = NetworkEventClassifier.responseKind(event)
        titleRow.addView(
            views.chip(
                responseKind,
                NetworkDebuggerRowPresentation.kindColor(responseKind, palette)
            )
        )
        header.addView(titleRow)
        header.addView(
            TextView(activity).apply {
                text = url
                setTextColor(textColor)
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextIsSelectable(true)
                setPadding(0, views.dp(7), 0, 0)
            }
        )
        val flags = NetworkDebuggerRowPresentation.flags(event, changedIds)
        if (flags.isNotBlank()) {
            header.addView(
                TextView(activity).apply {
                    text = flags
                    setTextColor(amber)
                    textSize = 9f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    setPadding(0, views.dp(6), 0, 0)
                }
            )
        }
        root.addView(
            header,
            LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, views.dp(7)) }
        )

        val actionScroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
        }
        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(
            views.detailButton("cURL") {
                copyText("cURL", NetworkDebuggerText.buildCurl(event))
            }
        )
        actions.addView(
            views.detailButton("REQUEST") {
                copyText("REQUEST", NetworkDebuggerText.buildRequestText(event, requestCookies))
            }
        )
        actions.addView(
            views.detailButton("REQ HEADERS") {
                copyText(
                    "REQUEST HEADERS",
                    NetworkDebuggerText.formatHeaders(requestHeadersList)
                )
            }
        )
        actions.addView(
            views.detailButton("RESPONSE") {
                copyText(
                    "RESPONSE",
                    NetworkDebuggerText.buildResponseCopy(event, requestCookies)
                )
            }
        )
        actions.addView(
            views.detailButton("RESP HEADERS") {
                copyText(
                    "RESPONSE HEADERS",
                    NetworkDebuggerText.formatHeaders(responseHeadersList)
                )
            }
        )
        if (canFetchBody(event)) {
            actions.addView(
                views.detailButton("GET BODY") {
                    val started = NetworkRequestActions.fetchMissingBody(activity, event)
                    Toast.makeText(
                        activity,
                        if (started) "Запрошено содержимое ответа"
                        else "Нельзя повторно получить этот ответ",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
        actions.addView(
            views.detailButton("EDIT / REPLAY") {
                showReplayEditor(event)
            }
        )
        if (responseKind == "JSON" && responseBody.isNotBlank()) {
            actions.addView(
                views.detailButton("JSON") {
                    copyText(
                        "JSON",
                        NetworkDebuggerText.prettyBody(responseBody, mime)
                    )
                }
            )
        }
        val decodeButton = views.detailButton("URL DECODE") {}
        actions.addView(decodeButton)
        actionScroll.addView(actions)
        root.addView(
            actionScroll,
            LinearLayout.LayoutParams(-1, views.dp(42)).apply {
                setMargins(0, 0, 0, views.dp(7))
            }
        )

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, views.dp(10))
        }

        val requestPanel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        requestPanel.addView(
            views.codeText(
                views.highlightPlain(
                    NetworkDebuggerText.buildRequestSummary(event),
                    query
                )
            )
        )
        val queryPairs = NetworkDebuggerText.queryPairs(url)
        if (queryPairs.isNotEmpty()) {
            requestPanel.addView(views.subtitle("QUERY PARAMETERS"))
            requestPanel.addView(
                views.plainBlock(
                    NetworkDebuggerText.formatPairs(queryPairs),
                    query
                )
            )
        }
        requestPanel.addView(views.subtitle("HEADERS"))
        requestPanel.addView(
            views.plainBlock(
                NetworkDebuggerText.formatHeaders(requestHeadersList),
                query
            )
        )
        val formPairs = NetworkDebuggerText.requestFormPairs(event)
        if (formPairs.isNotEmpty()) {
            requestPanel.addView(views.subtitle("FORM PARAMETERS"))
            requestPanel.addView(
                views.plainBlock(
                    NetworkDebuggerText.formatPairs(formPairs),
                    query
                )
            )
        }
        views.addCollapsible(content, "REQUEST", true, requestPanel)

        val responsePanel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        responsePanel.addView(
            views.codeText(
                views.highlightPlain(
                    NetworkDebuggerText.buildResponseSummary(event),
                    query
                )
            )
        )
        responsePanel.addView(views.subtitle("HEADERS"))
        responsePanel.addView(
            views.plainBlock(
                NetworkDebuggerText.formatHeaders(responseHeadersList),
                query
            )
        )
        views.addCollapsible(content, "RESPONSE", true, responsePanel)

        val bodyPanel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        val requestBody = event.optString("requestBody", "")
        if (requestBody.isNotBlank()) {
            bodyPanel.addView(views.subtitle("REQUEST BODY"))
            val requestJson = views.parseJson(requestBody)
            if (requestJson != null) {
                bodyPanel.addView(views.jsonTreeView(requestJson, "REQUEST JSON"))
                bodyPanel.addView(views.subtitle("RAW REQUEST BODY"))
            }
            bodyPanel.addView(
                views.codeText(
                    views.decorateResponseBody(
                        requestBody,
                        event.optString("requestMimeType", ""),
                        query
                    )
                )
            )
        }

        bodyPanel.addView(views.subtitle("RESPONSE BODY"))
        if (binary) {
            val size = bytes?.size?.toLong() ?: event.optLong("responseSize", -1L)
            val info = buildString {
                append(if (imageBitmap != null) "Image payload\n" else "Binary payload\n")
                append("MIME: ").append(mime.ifBlank { "application/octet-stream" }).append('\n')
                if (size >= 0) {
                    append("Size: ")
                        .append(NetworkDebuggerText.formatBytes(size))
                        .append(" (")
                        .append(size)
                        .append(" bytes)\n")
                }
                append("File: ").append(NetworkDebuggerText.suggestFileName(event, mime))
            }
            bodyPanel.addView(views.codeText(views.highlightPlain(info, query)))
            if (imageBitmap != null) {
                bodyPanel.addView(
                    ImageView(activity).apply {
                        setImageBitmap(imageBitmap)
                        adjustViewBounds = true
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setBackgroundColor(panel2)
                        contentDescription = "Изображение из ответа сервера"
                        setPadding(views.dp(6), views.dp(6), views.dp(6), views.dp(6))
                    },
                    LinearLayout.LayoutParams(-1, -2).apply {
                        setMargins(0, views.dp(6), 0, 0)
                    }
                )
            }
            if (bytes != null) {
                bodyPanel.addView(
                    views.compactButton("СОХРАНИТЬ БИНАРНИК") {
                        beginBinarySave(event, bytes, mime)
                    },
                    LinearLayout.LayoutParams(-1, views.dp(40)).apply {
                        setMargins(0, views.dp(6), 0, 0)
                    }
                )
            }
        } else {
            val responseJson = views.parseJson(responseBody)
            if (responseJson != null) {
                bodyPanel.addView(views.jsonTreeView(responseJson, "RESPONSE JSON"))
                bodyPanel.addView(views.subtitle("RAW RESPONSE BODY"))
            }
            bodyPanel.addView(
                views.codeText(
                    views.decorateResponseBody(
                        responseBody.ifBlank { "—" },
                        mime,
                        query
                    )
                )
            )
        }
        views.addCollapsible(content, "BODY", true, bodyPanel)

        views.addCollapsible(
            content,
            "TIMING",
            false,
            views.codeText(
                views.highlightPlain(
                    NetworkDebuggerText.buildTimingText(event),
                    query
                )
            )
        )
        views.addCollapsible(
            content,
            "COOKIES",
            false,
            views.codeText(
                views.highlightPlain(
                    requestCookies.ifBlank { "—" },
                    query
                )
            )
        )
        views.addCollapsible(
            content,
            "SOURCES",
            false,
            views.codeText(
                views.highlightPlain(
                    NetworkDebuggerText.buildSourcesText(event),
                    query
                )
            )
        )
        val mergedRaw = event.optJSONArray("_mergedEvents")
        val rawText =
            if (mergedRaw != null && mergedRaw.length() > 0) mergedRaw.toString(2)
            else event.toString(2)
        views.addCollapsible(
            content,
            "RAW",
            false,
            views.codeText(views.highlightPlain(rawText, query))
        )

        views.captureDisplayTexts(content, originalTexts)
        decodeButton.setOnClickListener {
            decoded = !decoded
            views.applyDecodedMode(content, originalTexts, decoded)
            decodeButton.text = if (decoded) "DECODED ✓" else "URL DECODE"
            decodeButton.setTextColor(if (decoded) accent else textColor)
        }

        val scroll = ScrollView(activity).apply {
            setBackgroundColor(bg)
            addView(content)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val detailsDialog = AlertDialog.Builder(activity).setView(root).create()
        dialog = detailsDialog
        val dm = activity.resources.displayMetrics
        detailsDialog.setOnShowListener {
            detailsDialog.window?.apply {
                setBackgroundDrawable(views.rounded(bg, 18f, line))
                setLayout(
                    (dm.widthPixels * 0.97).toInt(),
                    (dm.heightPixels * 0.92).toInt()
                )
                setGravity(Gravity.CENTER)
            }
        }
        detailsDialog.show()
    }

    private fun canFetchBody(event: JSONObject): Boolean {
        val body = NetworkEventClassifier.responseBodyText(event)
        val method = NetworkEventClassifier.methodOf(event)
        val url = event.optString("url", "")
        return method == "GET" &&
            (url.startsWith("http://") || url.startsWith("https://")) &&
            (body.isBlank() || body == "[unavailable]")
    }

    private fun showReplayEditor(event: JSONObject) {
        replayController.show(
            event = event,
            method = NetworkEventClassifier.methodOf(event),
            headers = NetworkDebuggerText.formatHeaders(
                NetworkDebuggerText.requestHeaderPairs(event)
            ).takeIf { it != "—" }.orEmpty()
        )
    }

    private fun copyText(label: String, value: String) {
        ResultDelivery.deliverText(activity, label, value)
    }

    private fun beginBinarySave(
        event: JSONObject,
        bytes: ByteArray,
        mime: String
    ) {
        val safeMime = mime.ifBlank { "application/octet-stream" }
        ResultDelivery.deliverBytes(
            activity,
            "Ответ",
            bytes,
            NetworkDebuggerText.suggestFileName(event, safeMime),
            safeMime
        )
    }


}
