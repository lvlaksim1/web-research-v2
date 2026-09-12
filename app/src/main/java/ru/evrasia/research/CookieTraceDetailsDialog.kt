package ru.evrasia.research

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

internal object CookieTraceDetailsDialog {
    data class Options(
        val domainLabel: String? = null,
        val verboseActions: Boolean = false,
        val directClipboard: Boolean = false,
        val conciseJavascriptRecipe: Boolean = false
    )

    private val ink = Color.rgb(3, 10, 15)
    private val surface = Color.rgb(7, 18, 25)
    private val surface2 = Color.rgb(10, 25, 34)
    private val line = Color.rgb(21, 57, 69)
    private val cyan = Color.rgb(0, 226, 239)
    private val white = Color.rgb(232, 244, 248)

    fun show(
        activity: NetworkDebuggerActivity,
        name: String,
        currentValue: String?,
        active: Boolean,
        history: List<JSONObject>,
        options: Options,
        onBack: () -> Unit
    ) {
        val birth = CookieTraceSupport.birthEvent(history)
        val origin = CookieTraceSupport.currentOrigin(history, currentValue)
        val regen = CookieTraceSupport.regenerationEvent(history, currentValue)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 12))
            setBackgroundColor(ink)
        }

        root.addView(
            block(
                activity,
                buildString {
                    options.domainLabel?.let { append("Домен: ").append(it).append('\n') }
                    if (options.domainLabel != null) append("Cookie: ")
                    append(name)
                    if (active) {
                        append("\n\nТЕКУЩЕЕ ЗНАЧЕНИЕ\n").append(currentValue.orEmpty())
                    } else {
                        append("\n\nСейчас cookie не активна")
                    }
                },
                true
            )
        )

        root.addView(sectionText(activity, "КАК ОНА РОДИЛАСЬ"))
        root.addView(
            block(
                activity,
                if (birth == null) {
                    "Рождение не зафиксировано. Cookie могла существовать до запуска трассировки."
                } else {
                    CookieTraceSupport.formatOrigin(birth)
                },
                false
            )
        )

        if (origin != null && origin !== birth) {
            root.addView(sectionText(activity, "ИСТОЧНИК ТЕКУЩЕГО ЗНАЧЕНИЯ"))
            root.addView(block(activity, CookieTraceSupport.formatOrigin(origin), false))
        }

        root.addView(sectionText(activity, "КАК ПОЛУЧИТЬ ЕЁ СНОВА"))
        root.addView(block(activity, regenerationRecipe(regen, options.conciseJavascriptRecipe), false))

        if (regen != null) {
            when (regen.optString("origin", "")) {
                "HTTP_RESPONSE", "LIKELY_HTTP_RESPONSE" -> {
                    val url = regen.optString("url", "")
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        root.addView(
                            actionButton(
                                activity,
                                if (regen.optString("confidence") == "EXACT") {
                                    "ПОВТОРИТЬ ИСХОДНЫЙ ЗАПРОС"
                                } else {
                                    "ПОВТОРИТЬ ВЕРОЯТНЫЙ ЗАПРОС"
                                }
                            ) {
                                val headers = CookieTraceSupport.headersMap(regen.optJSONObject("requestHeaders"))
                                val original = JSONObject()
                                    .put("url", url)
                                    .put("method", regen.optString("method", "GET"))
                                if (regen.has("_storeId")) original.put("_storeId", regen.optLong("_storeId"))
                                val ok = NetworkRequestActions.replay(
                                    activity,
                                    original,
                                    regen.optString("method", "GET"),
                                    url,
                                    headers,
                                    regen.optString("requestBody", "")
                                )
                                Toast.makeText(
                                    activity,
                                    if (ok) "Запрос отправляется" else "Не удалось повторить запрос",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                        val curlLabel = if (options.verboseActions) "КОПИРОВАТЬ cURL" else "cURL"
                        root.addView(
                            actionButton(activity, curlLabel) {
                                copyText(
                                    activity,
                                    "cURL",
                                    CookieTraceSupport.curlFor(regen),
                                    options.directClipboard
                                )
                            }
                        )
                    }
                }

                "JAVASCRIPT" -> {
                    if (regen.optString("raw", "").isNotBlank()) {
                        val jsLabel = if (options.verboseActions) "КОПИРОВАТЬ JS SETTER" else "JS SETTER"
                        root.addView(
                            actionButton(activity, jsLabel) {
                                copyText(
                                    activity,
                                    "JS setter",
                                    CookieTraceSupport.jsSetter(regen),
                                    options.directClipboard
                                )
                            }
                        )
                    }
                }
            }
        }

        root.addView(sectionText(activity, "ИСТОРИЯ"))
        if (history.isEmpty()) root.addView(block(activity, "История пока отсутствует.", false))
        history.asReversed().forEach {
            root.addView(block(activity, CookieTraceSupport.formatHistory(it), false))
        }

        val scroll = ScrollView(activity).apply {
            setBackgroundColor(ink)
            addView(root)
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("COOKIE · " + name)
            .setView(scroll)
            .setNegativeButton("Назад", null)
            .create()
        val dm = activity.resources.displayMetrics
        dialog.window?.apply {
            setBackgroundDrawable(round(activity, ink, 16, line))
            attributes = attributes.apply {
                width = (dm.widthPixels * 0.97).toInt()
                height = (dm.heightPixels * 0.92).toInt()
            }
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                setTextColor(cyan)
                setOnClickListener {
                    dialog.dismiss()
                    onBack()
                }
            }
        }
        dialog.show()
    }

    private fun regenerationRecipe(
        event: JSONObject?,
        conciseJavascriptRecipe: Boolean
    ): String {
        if (event == null) {
            return "Недостаточно данных, чтобы предложить способ воспроизведения. Нужно поймать момент создания cookie после запуска приложения."
        }
        return when (event.optString("origin", "")) {
            "HTTP_RESPONSE" -> buildString {
                append("Cookie пришла в ответе Set-Cookie. Чтобы сервер сгенерировал её снова, повторите исходный HTTP-запрос.\n\n")
                append(CookieTraceSupport.requestRecipe(event))
                append("\n\nНиже есть кнопка повторения запроса. Новый ответ может выдать новое значение cookie, если серверная логика допускает повторную генерацию.")
            }

            "LIKELY_HTTP_RESPONSE" -> buildString {
                append("Cookie появилась сразу после этого запроса, но Set-Cookie в исходном ответе перехватить не удалось. Поэтому источник вероятный, а не доказанный.\n\n")
                append(CookieTraceSupport.requestRecipe(event))
                append("\n\nМожно повторить этот запрос и проверить, создастся ли cookie снова.")
            }

            "JAVASCRIPT" -> buildString {
                append("Cookie записана JavaScript через ")
                    .append(event.optString("mechanism", "JavaScript"))
                    .append(".\n")
                if (event.optString("stack", "").isNotBlank()) {
                    append("Главный ориентир — JS stack ниже: он показывает код, который выполнил запись.\n")
                }
                if (conciseJavascriptRecipe) {
                    append("\nДля генерации нового значения нужно повторить действие сайта, приведшее к этому вызову. Простое выполнение setter-а обычно только запишет уже известное значение.\n\n")
                } else {
                    append("\nДля точного воспроизведения нового значения нужно повторить действие сайта, которое приводит к этому вызову. Простое повторение setter-а обычно лишь запишет старое значение.\n\n")
                }
                append("Setter для проверки:\n").append(CookieTraceSupport.jsSetter(event))
            }

            else ->
                "Источник рождения пока не доказан. Трассировка видит факт появления/изменения cookie, но не может гарантированно назвать код или HTTP-ответ, создавший её."
        }
    }

    private fun copyText(
        activity: NetworkDebuggerActivity,
        label: String,
        value: String,
        directClipboard: Boolean
    ) {
        if (!directClipboard) {
            ResultDelivery.deliverText(activity, label, value)
            return
        }
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(activity, label + " скопирован", Toast.LENGTH_SHORT).show()
    }

    private fun block(
        activity: NetworkDebuggerActivity,
        textValue: String,
        strong: Boolean
    ) = TextView(activity).apply {
        text = textValue
        setTextColor(white)
        textSize = if (strong) 11f else 10f
        typeface = if (strong) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.MONOSPACE
        setTextIsSelectable(true)
        isFocusable = true
        isFocusableInTouchMode = true
        setPadding(dp(activity, 10), dp(activity, 9), dp(activity, 10), dp(activity, 9))
        background = round(activity, surface2, 9, line)
    }

    private fun sectionText(activity: NetworkDebuggerActivity, value: String) =
        TextView(activity).apply {
            text = value
            setTextColor(cyan)
            textSize = 9f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = .08f
            setPadding(dp(activity, 4), dp(activity, 10), dp(activity, 4), dp(activity, 5))
        }

    private fun actionButton(
        activity: NetworkDebuggerActivity,
        label: String,
        click: () -> Unit
    ) = Button(activity).apply {
        text = label
        isAllCaps = false
        setTextColor(cyan)
        textSize = 9.5f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(activity, 9), 0, dp(activity, 9), 0)
        background = round(activity, surface, 9, line)
        setOnClickListener { click() }
    }

    private fun dp(activity: NetworkDebuggerActivity, value: Int) =
        (value * activity.resources.displayMetrics.density).toInt()

    private fun round(
        activity: NetworkDebuggerActivity,
        fill: Int,
        radius: Int,
        stroke: Int? = null
    ) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(activity, radius).toFloat()
        stroke?.let { setStroke(dp(activity, 1), it) }
    }
}
