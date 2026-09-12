package ru.evrasia.research

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

internal class NetworkDebuggerDetailViews(
    private val activity: AppCompatActivity
) {
    private val palette get() = WebUiTheme.palette(activity)
    private val panel get() = palette.card
    private val panel2 get() = palette.address
    private val line get() = palette.divider
    private val accent get() = palette.accent
    private val textColor get() = palette.text
    private val muted get() = palette.secondary
    private val cyan get() = palette.accent
    private val amber get() = palette.orange
    private val violet get() = palette.blue

    fun jsonTreeView(rootValue: Any, title: String): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(panel2, 9f, line)
            setPadding(dp(6), dp(5), dp(6), dp(5))
        }
        addJsonNode(root, title, rootValue, 0, false)
        return root
    }

    fun parseJson(raw: String): Any? {
        val text = raw.trim()
        if (text.isBlank()) return null
        return try {
            when {
                text.startsWith("{") -> JSONObject(text)
                text.startsWith("[") -> JSONArray(text)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun detailButton(label: String, click: () -> Unit) =
        compactButton(label, click).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 9f
            layoutParams = LinearLayout.LayoutParams(-2, dp(38)).apply {
                marginEnd = dp(5)
            }
        }

    fun chip(label: String, color: Int) =
        TextView(activity).apply {
            text = label
            setTextColor(color)
            textSize = 10f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = rounded(panel2, 8f, line)
        }

    fun subtitle(label: String) =
        TextView(activity).apply {
            text = label
            setTextColor(muted)
            textSize = 9f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = .08f
            setPadding(dp(3), dp(6), dp(3), dp(4))
        }

    fun plainBlock(raw: String, query: String) =
        TextView(activity).apply {
            text = highlightPlain(raw.ifBlank { "—" }, query)
            setTextColor(textColor)
            textSize = 10.5f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = rounded(panel2, 9f, Color.rgb(40, 64, 70))
        }

    fun addCollapsible(
        root: LinearLayout,
        title: String,
        open: Boolean,
        body: View
    ) {
        var expanded = open
        val button = Button(activity).apply {
            text = title
            setTextColor(cyan)
            textSize = 10f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAllCaps = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(12), 0, dp(12), 0)
            background = rounded(panel, 10f, line)
        }
        setExpandIcon(button, expanded)
        body.visibility = if (expanded) View.VISIBLE else View.GONE
        button.setOnClickListener {
            expanded = !expanded
            body.visibility = if (expanded) View.VISIBLE else View.GONE
            button.text = title
            setExpandIcon(button, expanded)
        }
        root.addView(
            button,
            LinearLayout.LayoutParams(-1, dp(38)).apply {
                setMargins(0, dp(5), 0, dp(4))
            }
        )
        root.addView(body, LinearLayout.LayoutParams(-1, -2))
    }

    fun captureDisplayTexts(
        view: View,
        originals: MutableMap<TextView, CharSequence>
    ) {
        when (view) {
            is Button -> Unit
            is TextView -> originals[view] = SpannableString(view.text)
            is ViewGroup ->
                for (index in 0 until view.childCount) {
                    captureDisplayTexts(view.getChildAt(index), originals)
                }
        }
    }

    fun applyDecodedMode(
        view: View,
        originals: Map<TextView, CharSequence>,
        decoded: Boolean
    ) {
        when (view) {
            is Button -> Unit
            is TextView -> {
                val original = originals[view] ?: view.text
                view.text =
                    if (decoded) {
                        NetworkDebuggerText.decodePercentText(original.toString())
                    } else {
                        original
                    }
            }

            is ViewGroup ->
                for (index in 0 until view.childCount) {
                    applyDecodedMode(view.getChildAt(index), originals, decoded)
                }
        }
    }

    fun decorateResponseBody(
        raw: String,
        mime: String,
        query: String
    ): CharSequence {
        val pretty = NetworkDebuggerText.prettyBody(raw, mime)
        val spannable = SpannableString(pretty)
        if (
            mime.contains("json", true) ||
            pretty.trim().startsWith("{") ||
            pretty.trim().startsWith("[")
        ) {
            colorRegex(
                spannable,
                Regex("\"(?:\\\\.|[^\"\\\\])*\"(?=\\s*:)", RegexOption.DOT_MATCHES_ALL),
                cyan
            )
            colorRegex(
                spannable,
                Regex("(?<=:)\\s*\"(?:\\\\.|[^\"\\\\])*\"", RegexOption.DOT_MATCHES_ALL),
                accent
            )
            colorRegex(spannable, Regex("\\b(true|false|null)\\b"), violet)
            colorRegex(
                spannable,
                Regex("(?<![A-Za-z0-9_])-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?"),
                amber
            )
        } else if (
            mime.contains("html", true) ||
            mime.contains("xml", true) ||
            pretty.trim().startsWith("<")
        ) {
            colorRegex(spannable, Regex("</?[A-Za-z][^>]*>"), cyan)
            colorRegex(
                spannable,
                Regex("\\b[A-Za-z_:][-A-Za-z0-9_:.]*(?=\\s*=)"),
                accent
            )
            colorRegex(spannable, Regex("\"[^\"]*\"|'[^']*'"), amber)
        }
        applyQueryHighlight(spannable, query)
        return spannable
    }

    fun highlightPlain(raw: String, query: String): CharSequence {
        val spannable = SpannableString(raw)
        applyQueryHighlight(spannable, query)
        return spannable
    }

    fun codeText(value: CharSequence) =
        TextView(activity).apply {
            text = value
            setTextColor(textColor)
            textSize = 10.5f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = rounded(panel2, 9f, Color.rgb(40, 64, 70))
        }

    fun compactButton(label: String, click: () -> Unit) =
        Button(activity).apply {
            text = label
            setTextColor(textColor)
            textSize = 10f
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(10), 0, dp(10), 0)
            background = rounded(panel2, 10f, line)
            setOnClickListener { click() }
        }

    fun compactIconButton(
        icon: TechIconDrawable.Kind,
        description: String,
        click: () -> Unit
    ) =
        Button(activity).apply {
            text = ""
            contentDescription = description
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            background = rounded(panel2, 12f, line)
            foreground = TechIconDrawable(icon, accent)
            setOnClickListener { click() }
        }

    private fun setExpandIcon(button: Button, expanded: Boolean) {
        val drawable = TechIconDrawable(
            if (expanded) TechIconDrawable.Kind.EXPAND_LESS else TechIconDrawable.Kind.EXPAND_MORE,
            cyan,
            0.84f
        ).apply {
            setBounds(0, 0, dp(22), dp(22))
        }
        button.setCompoundDrawablesRelative(null, null, drawable, null)
        button.compoundDrawablePadding = dp(7)
    }

    fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    fun rounded(
        fill: Int,
        radius: Float,
        stroke: Int = Color.TRANSPARENT
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radius.toInt()).toFloat()
            if (stroke != Color.TRANSPARENT) {
                setStroke(dp(1), stroke)
            }
        }

    private fun addJsonNode(
        parent: LinearLayout,
        label: String,
        value: Any?,
        depth: Int,
        openInitially: Boolean
    ) {
        val indent = dp(depth.coerceAtMost(12) * 10)
        when (value) {
            is JSONObject, is JSONArray -> {
                val count =
                    if (value is JSONObject) value.length()
                    else (value as JSONArray).length()
                val node = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                }
                val children = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = if (openInitially) View.VISIBLE else View.GONE
                }
                var loaded = false
                val button = Button(activity).apply {
                    text =
                        "$label  " +
                            if (value is JSONObject) "{$count}" else "[$count]"
                    setTextColor(cyan)
                    textSize = 10f
                    typeface = Typeface.MONOSPACE
                    isAllCaps = false
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    minHeight = 0
                    minimumHeight = 0
                    setPadding(indent + dp(8), 0, dp(8), 0)
                    background = rounded(panel, 8f, Color.TRANSPARENT)
                    val treeIcon = TechIconDrawable(
                        if (openInitially) TechIconDrawable.Kind.EXPAND_MORE else TechIconDrawable.Kind.CHEVRON_RIGHT,
                        cyan,
                        0.86f
                    ).apply { setBounds(0, 0, dp(20), dp(20)) }
                    setCompoundDrawablesRelative(treeIcon, null, null, null)
                    compoundDrawablePadding = dp(5)
                }

                fun load() {
                    if (loaded) return
                    loaded = true
                    var shown = 0
                    val limit = 300
                    if (value is JSONObject) {
                        val keys = value.keys()
                        while (keys.hasNext() && shown < limit) {
                            val key = keys.next()
                            addJsonNode(children, key, value.opt(key), depth + 1, false)
                            shown++
                        }
                    } else if (value is JSONArray) {
                        for (index in 0 until minOf(value.length(), limit)) {
                            addJsonNode(children, "[$index]", value.opt(index), depth + 1, false)
                            shown++
                        }
                    }
                    if (count > shown) {
                        children.addView(
                            TextView(activity).apply {
                                text = "… ещё ${count - shown} элементов (RAW содержит всё)"
                                setTextColor(muted)
                                textSize = 9f
                                typeface = Typeface.MONOSPACE
                                setPadding(
                                    indent + dp(18),
                                    dp(6),
                                    dp(6),
                                    dp(6)
                                )
                            }
                        )
                    }
                }

                if (openInitially) load()
                button.setOnClickListener {
                    if (children.visibility == View.VISIBLE) {
                        children.visibility = View.GONE
                        button.text =
                            "$label  " +
                                if (value is JSONObject) "{$count}" else "[$count]"
                        val collapsedIcon = TechIconDrawable(
                            TechIconDrawable.Kind.CHEVRON_RIGHT,
                            cyan,
                            0.86f
                        ).apply { setBounds(0, 0, dp(20), dp(20)) }
                        button.setCompoundDrawablesRelative(collapsedIcon, null, null, null)
                    } else {
                        load()
                        children.visibility = View.VISIBLE
                        button.text =
                            "$label  " +
                                if (value is JSONObject) "{$count}" else "[$count]"
                        val expandedIcon = TechIconDrawable(
                            TechIconDrawable.Kind.EXPAND_MORE,
                            cyan,
                            0.86f
                        ).apply { setBounds(0, 0, dp(20), dp(20)) }
                        button.setCompoundDrawablesRelative(expandedIcon, null, null, null)
                    }
                }
                node.addView(button, LinearLayout.LayoutParams(-1, dp(34)))
                node.addView(children)
                parent.addView(node)
            }

            else -> {
                val shown = when (value) {
                    null, JSONObject.NULL -> "null"
                    is String ->
                        if (value.length > 1200) value.take(1200) + "…" else value
                    else -> value.toString()
                }
                parent.addView(
                    TextView(activity).apply {
                        text = "$label: $shown"
                        setTextColor(textColor)
                        textSize = 10f
                        typeface = Typeface.MONOSPACE
                        setTextIsSelectable(true)
                        setPadding(
                            indent + dp(8),
                            dp(5),
                            dp(8),
                            dp(5)
                        )
                    }
                )
            }
        }
    }

    private fun colorRegex(
        spannable: SpannableString,
        regex: Regex,
        color: Int
    ) {
        regex.findAll(spannable.toString()).forEach { match ->
            spannable.setSpan(
                ForegroundColorSpan(color),
                match.range.first,
                match.range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun applyQueryHighlight(
        spannable: SpannableString,
        query: String
    ) {
        if (query.isBlank()) return
        var position = spannable.toString().indexOf(query, 0, true)
        while (position >= 0) {
            spannable.setSpan(
                BackgroundColorSpan(Color.rgb(90, 110, 30)),
                position,
                position + query.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            position = spannable.toString().indexOf(
                query,
                position + query.length,
                true
            )
        }
    }
}
