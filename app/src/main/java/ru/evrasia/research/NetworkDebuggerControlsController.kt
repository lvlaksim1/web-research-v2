package ru.evrasia.research

import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

internal class NetworkDebuggerControlsController(
    private val activity: AppCompatActivity,
    private val typeFilters: List<String>,
    private val methodFilters: List<String>,
    private val onBack: () -> Unit,
    private val onRecordingToggle: () -> Unit,
    private val onClear: () -> Unit,
    private val onChanged: () -> Unit
) {
    data class Filters(
        val domain: String,
        val type: String,
        val method: String,
        val query: String,
        val mergeMode: Boolean
    )

    private var domains: List<String> = listOf("Все домены")
    private var domain = "Все домены"
    private var type = "ALL"
    private var method = "ALL"
    private var query = ""
    private var mergeMode = false
    private var recordButton: Button? = null

    private val palette get() = WebUiTheme.palette(activity)
    private val panel get() = palette.card
    private val panel2 get() = palette.address
    private val line get() = palette.divider
    private val accent get() = palette.accent
    private val textColor get() = palette.text
    private val muted get() = palette.secondary

    fun createBar(recording: Boolean): LinearLayout {
        val controls = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(5), dp(6), dp(6))
            setBackgroundColor(panel)
        }
        fun addControl(button: Button) {
            controls.addView(
                button,
                LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                    marginEnd = dp(4)
                }
            )
        }

        addControl(chromeButton(TechIconDrawable.Kind.BACK, "Назад") { onBack() })
        addControl(chromeButton(TechIconDrawable.Kind.FILTER, "Фильтр домена") { showDomainPopup(it) })
        addControl(chromeButton(TechIconDrawable.Kind.SEARCH, "Поиск") { showSearchPopup(it) })

        recordButton = chromeButton(
            if (recording) TechIconDrawable.Kind.STOP else TechIconDrawable.Kind.RECORD,
            if (recording) "Остановить запись" else "Начать запись",
            if (recording) palette.red else accent
        ) {
            onRecordingToggle()
        }
        addControl(recordButton!!)

        addControl(chromeButton(TechIconDrawable.Kind.DELETE, "Очистить журнал") { onClear() })
        controls.addView(
            chromeButton(TechIconDrawable.Kind.MENU, "Меню") { showNetworkMenu(it) },
            LinearLayout.LayoutParams(dp(48), dp(48))
        )
        return controls
    }

    fun filters(): Filters =
        Filters(
            domain = domain,
            type = type,
            method = method,
            query = query,
            mergeMode = mergeMode
        )

    fun setDomains(values: List<String>) {
        val normalized = buildList {
            add("Все домены")
            values.filter { it.isNotBlank() && it != "Все домены" }
                .distinct()
                .sorted()
                .forEach(::add)
        }
        domains = normalized
        if (domain !in normalized) domain = "Все домены"
    }

    fun updateRecording(recording: Boolean) {
        recordButton?.apply {
            text = ""
            contentDescription =
                if (recording) "Остановить запись" else "Начать запись"
            foreground = TechIconDrawable(
                if (recording) TechIconDrawable.Kind.STOP else TechIconDrawable.Kind.RECORD,
                if (recording) palette.red else accent
            )
        }
    }

    private fun showDomainPopup(anchor: View) {
        showSelectionPopup(
            anchor = anchor,
            title = "Домен",
            values = domains,
            current = domain
        ) { value ->
            domain = value
            onChanged()
        }
    }

    private fun showSearchPopup(anchor: View) {
        var popup: PopupWindow? = null
        val content = popupPanel()
        content.addView(popupHeader("Поиск") { popup?.dismiss() })
        val input = EditText(activity).apply {
            setText(query)
            setSelection(text.length)
            hint = "URL, headers, body..."
            setHintTextColor(muted)
            setTextColor(textColor)
            textSize = 12f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = rounded(panel2, 11f, line)
            setPadding(dp(10), 0, dp(10), 0)
            setOnEditorActionListener { _, id, _ ->
                if (id == EditorInfo.IME_ACTION_SEARCH) {
                    query = text.toString()
                    onChanged()
                    popup?.dismiss()
                    true
                } else {
                    false
                }
            }
        }
        content.addView(
            input,
            LinearLayout.LayoutParams(-1, dp(42)).apply {
                setMargins(dp(8), dp(5), dp(8), dp(7))
            }
        )
        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        actions.addView(
            popupAction("Очистить") {
                query = ""
                onChanged()
                popup?.dismiss()
            },
            LinearLayout.LayoutParams(0, dp(42), 1f)
        )
        actions.addView(
            popupAction("Найти") {
                query = input.text.toString()
                onChanged()
                popup?.dismiss()
            },
            LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                marginStart = dp(6)
            }
        )
        content.addView(
            actions,
            LinearLayout.LayoutParams(-1, dp(42)).apply {
                setMargins(dp(8), 0, dp(8), dp(8))
            }
        )
        popup = buildPopup(content, 310).apply {
            inputMethodMode = PopupWindow.INPUT_METHOD_NEEDED
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        showSearchAboveKeyboard(popup, content, anchor, 310)
        input.post { input.requestFocus() }
    }

    private fun showNetworkMenu(anchor: View) {
        var popup: PopupWindow? = null
        val content = popupPanel()
        content.addView(popupHeader("Фильтры и режимы") { popup?.dismiss() })
        content.addView(
            popupRow(
                if (mergeMode) "Объединено ✓" else "Раздельно",
                mergeMode
            ) {
                mergeMode = !mergeMode
                onChanged()
                popup?.dismiss()
            }
        )
        content.addView(
            popupRow(
                "Тип ответа: $type",
                type != "ALL"
            ) {
                popup?.dismiss()
                showSelectionPopup(
                    anchor,
                    "Тип ответа",
                    typeFilters,
                    type
                ) { value ->
                    type = value
                    onChanged()
                }
            }
        )
        content.addView(
            popupRow(
                "Метод: $method",
                method != "ALL"
            ) {
                popup?.dismiss()
                showSelectionPopup(
                    anchor,
                    "Метод",
                    methodFilters,
                    method
                ) { value ->
                    method = value
                    onChanged()
                }
            }
        )
        popup = buildPopup(content, 275)
        showAboveRight(popup, content, anchor, 275)
    }

    private fun showSelectionPopup(
        anchor: View,
        title: String,
        values: List<String>,
        current: String,
        onSelect: (String) -> Unit
    ) {
        var popup: PopupWindow? = null
        val content = popupPanel()
        content.addView(popupHeader(title) { popup?.dismiss() })
        val scrollBody = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        values.forEach { value ->
            scrollBody.addView(
                popupRow(
                    if (value == current) "$value ✓" else value,
                    value == current
                ) {
                    onSelect(value)
                    popup?.dismiss()
                }
            )
        }
        val scroll = ScrollView(activity).apply {
            addView(scrollBody)
        }
        content.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )
        popup = buildPopup(
            content,
            285,
            maxHeight = (activity.resources.displayMetrics.heightPixels * 0.64f).toInt()
        )
        showAboveRight(popup, content, anchor, 285)
    }

    private fun popupPanel() =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = rounded(panel, 16f, line)
        }

    private fun popupHeader(title: String, close: () -> Unit) =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                chromeButton(TechIconDrawable.Kind.CLOSE, "Закрыть") { close() },
                LinearLayout.LayoutParams(dp(48), dp(48))
            )
            addView(
                TextView(activity).apply {
                    text = title
                    setTextColor(textColor)
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(9), 0, 0, 0)
                },
                LinearLayout.LayoutParams(0, dp(38), 1f)
            )
        }

    private fun popupRow(
        label: String,
        active: Boolean,
        click: () -> Unit
    ) =
        Button(activity).apply {
            text = label
            isAllCaps = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textSize = 12.5f
            setTextColor(if (active) accent else textColor)
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(12), 0, dp(12), 0)
            background = rounded(if (active) panel2 else panel, 10f, line)
            setOnClickListener { click() }
            layoutParams =
                LinearLayout.LayoutParams(-1, dp(42)).apply {
                    setMargins(dp(3), dp(2), dp(3), dp(2))
                }
        }

    private fun popupAction(label: String, click: () -> Unit) =
        Button(activity).apply {
            text = label
            isAllCaps = false
            textSize = 12f
            setTextColor(textColor)
            background = rounded(panel2, 11f, line)
            setOnClickListener { click() }
        }

    private fun chromeButton(
        icon: TechIconDrawable.Kind,
        description: String,
        iconColor: Int = accent,
        click: (View) -> Unit
    ) =
        Button(activity).apply {
            text = ""
            contentDescription = description
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            background = rounded(panel2, 14f, line)
            foreground = TechIconDrawable(icon, iconColor)
            setOnClickListener { click(this) }
        }

    private fun buildPopup(
        content: LinearLayout,
        widthDp: Int,
        maxHeight: Int = WindowManager.LayoutParams.WRAP_CONTENT
    ): PopupWindow =
        PopupWindow(content, dp(widthDp), maxHeight, true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = dp(10).toFloat()
        }

    private fun showSearchAboveKeyboard(
        popup: PopupWindow,
        content: View,
        anchor: View,
        widthDp: Int
    ) {
        val width = dp(widthDp)
        content.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val height = content.measuredHeight
        popup.width = width
        popup.height = height

        val root = anchor.rootView
        fun position(): IntArray {
            val visibleFrame = Rect()
            root.getWindowVisibleDisplayFrame(visibleFrame)
            val anchorLocation = IntArray(2)
            anchor.getLocationOnScreen(anchorLocation)
            val x = (anchorLocation[0] + anchor.width - width).coerceAtLeast(dp(4))
            val preferredY = anchorLocation[1] - height
            val maximumY = visibleFrame.bottom - height - dp(8)
            val minimumY = visibleFrame.top + dp(4)
            val y = minOf(preferredY, maximumY).coerceAtLeast(minimumY)
            return intArrayOf(x, y)
        }

        val initial = position()
        popup.showAtLocation(anchor.rootView, Gravity.TOP or Gravity.START, initial[0], initial[1])

        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            if (!popup.isShowing) return@OnGlobalLayoutListener
            val next = position()
            popup.update(next[0], next[1], width, height)
        }
        root.viewTreeObserver.addOnGlobalLayoutListener(listener)
        popup.setOnDismissListener {
            if (root.viewTreeObserver.isAlive) {
                root.viewTreeObserver.removeOnGlobalLayoutListener(listener)
            }
        }
    }

    private fun showAboveRight(
        popup: PopupWindow,
        content: View,
        anchor: View,
        widthDp: Int
    ) {
        val width = dp(widthDp)
        content.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        if (popup.height == WindowManager.LayoutParams.WRAP_CONTENT) {
            popup.height = content.measuredHeight
        }
        val height =
            if (popup.height > 0) popup.height else content.measuredHeight
        popup.width = width
        popup.height = height
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val x = (location[0] + anchor.width - width).coerceAtLeast(dp(4))
        val y = (location[1] - height).coerceAtLeast(dp(4))
        popup.showAtLocation(anchor.rootView, Gravity.TOP or Gravity.START, x, y)
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    private fun rounded(
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
}
