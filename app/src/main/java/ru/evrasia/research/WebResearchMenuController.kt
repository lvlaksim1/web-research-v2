package ru.evrasia.research

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

internal class WebResearchMenuController(
    private val activity: AppCompatActivity,
    private val bookmarkController: WebBookmarkController,
    private val webViewController: WebResearchWebViewController,
    private val paletteProvider: () -> WebUiTheme.Palette,
    private val currentPageProvider: () -> String,
    private val onAccentColor: (Int, Boolean) -> Unit
) {
    private var activeBrowserMenu: Dialog? = null
    private var activeSheetDialog: Dialog? = null
    private var activeSheetCloseButton: Button? = null

    fun toggleBrowserMenu() {
        activeBrowserMenu?.takeIf { it.isShowing }?.let {
            it.dismiss()
            return
        }
        activeBrowserMenu = showBottomSheet("Меню") { dialog ->
            addSection("СТРАНИЦА")
            addMenuRow(TechIconDrawable.Kind.BOOKMARK_ADD, "Добавить в закладки", currentHost()) {
                bookmarkController.save(currentPage())
                dialog.dismiss()
            }
            addMenuRow(TechIconDrawable.Kind.BOOKMARKS, "Закладки", "${bookmarkController.all().size} сохранено") {
                dialog.dismiss()
                showBookmarksSheet()
            }
            addSiteVersionRow(dialog)

            addSection("ДАННЫЕ САЙТА")
            val cookieCount = cookieCount()
            addMenuRow(TechIconDrawable.Kind.COOKIE, "Cookies", "${currentHost()} · $cookieCount cookies") {
                dialog.dismiss()
                showCookiesSheet()
            }
            addMenuRow(TechIconDrawable.Kind.DELETE, "Удалить cookies домена", if (cookieCount > 0) "$cookieCount cookies" else "Нет cookies") {
                dialog.dismiss()
                confirmClearCookies(cookieCount)
            }
            addMenuRow(TechIconDrawable.Kind.APPEARANCE, "Интерфейс", "Тема и цвет элементов") {
                dialog.dismiss()
                showInterfaceMenu()
            }

            addSection("ПРИЛОЖЕНИЕ")
            addMenuRow(TechIconDrawable.Kind.INFO, "О приложении", "web research") {
                dialog.dismiss()
                showAbout()
            }
        }
    }

    fun updateAccent(color: Int) {
        activeSheetCloseButton?.foreground = TechIconDrawable(TechIconDrawable.Kind.CLOSE, color)
    }

    fun dismiss() {
        activeBrowserMenu?.dismiss()
        activeSheetDialog?.dismiss()
        activeBrowserMenu = null
        activeSheetDialog = null
        activeSheetCloseButton = null
    }

    private fun LinearLayout.addSiteVersionRow(dialog: Dialog) {
        val palette = palette()
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(9), dp(14), dp(10))
        }
        container.addView(TextView(activity).apply {
            text = "Версия сайта"
            setTextColor(palette.text)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        })
        val selector = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(7), 0, 0)
        }
        fun modeButton(label: String, desktop: Boolean): Button = Button(activity).apply {
            text = label
            isAllCaps = false
            textSize = 12f
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            fun render() {
                val current = palette()
                val selected = webViewController.isDesktopMode() == desktop
                setTextColor(if (selected) WebUiTheme.contrastText(current.accent) else current.text)
                background = rounded(if (selected) current.accent else current.address, 12f, if (selected) current.accent else current.divider)
            }
            render()
            setOnClickListener {
                webViewController.setDesktopMode(desktop)
                for (index in 0 until selector.childCount) {
                    (selector.getChildAt(index) as? Button)?.let { button ->
                        val current = palette()
                        val selected = (button.text.toString() == "ПК") == webViewController.isDesktopMode()
                        button.setTextColor(if (selected) WebUiTheme.contrastText(current.accent) else current.text)
                        button.background = rounded(if (selected) current.accent else current.address, 12f, if (selected) current.accent else current.divider)
                    }
                }
                dialog.dismiss()
            }
        }
        selector.addView(modeButton("Мобильная", false), LinearLayout.LayoutParams(0, dp(38), 1f))
        selector.addView(modeButton("ПК", true), LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginStart = dp(6) })
        container.addView(selector)
        addView(container, LinearLayout.LayoutParams(-1, -2))
        addDivider()
    }

    private fun showBookmarksSheet() {
        showBottomSheet("Закладки") { dialog ->
            addMenuRow(TechIconDrawable.Kind.BOOKMARK_ADD, "Добавить текущую страницу", currentPage()) {
                bookmarkController.save(currentPage())
                dialog.dismiss()
                showBookmarksSheet()
            }
            val saved = bookmarkController.all()
            if (saved.isEmpty()) {
                addView(TextView(activity).apply {
                    text = "Закладок пока нет"
                    setTextColor(palette().secondary)
                    textSize = 14f
                    setPadding(dp(14), dp(20), dp(14), dp(24))
                })
            } else {
                saved.forEach { url ->
                    val row = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(14), dp(7), dp(8), dp(7))
                        setOnClickListener {
                            bookmarkController.open(url)
                            dialog.dismiss()
                        }
                    }
                    val labels = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                    labels.addView(TextView(activity).apply {
                        text = hostOf(url).ifBlank { url }
                        setTextColor(palette().text)
                        textSize = 14f
                        typeface = Typeface.DEFAULT_BOLD
                    })
                    labels.addView(TextView(activity).apply {
                        text = url
                        setTextColor(palette().secondary)
                        textSize = 11f
                        maxLines = 1
                    })
                    row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
                    row.addView(Button(activity).apply {
                        text = ""
                        contentDescription = "Удалить закладку"
                        minWidth = 0
                        minimumWidth = 0
                        minHeight = 0
                        minimumHeight = 0
                        setPadding(0, 0, 0, 0)
                        background = ColorDrawable(Color.TRANSPARENT)
                        foreground = TechIconDrawable(TechIconDrawable.Kind.DELETE, palette().red)
                        setOnClickListener {
                            bookmarkController.delete(url)
                            dialog.dismiss()
                            showBookmarksSheet()
                        }
                    }, LinearLayout.LayoutParams(dp(48), dp(48)))
                    addView(row)
                    addDivider()
                }
            }
        }
    }

    private fun showCookiesSheet() {
        val page = currentPage()
        val raw = CookieManager.getInstance().getCookie(page).orEmpty()
        val cookies = raw.split(';').map { it.trim() }.filter { it.isNotBlank() }
        showBottomSheet("Cookies") { dialog ->
            addView(TextView(activity).apply {
                text = "${currentHost()} · ${cookies.size} cookies"
                setTextColor(palette().secondary)
                textSize = 12f
                setPadding(dp(14), dp(2), dp(14), dp(10))
            })
            if (cookies.isEmpty()) {
                addView(TextView(activity).apply {
                    text = "Для текущего домена cookies не найдены"
                    setTextColor(palette().text)
                    textSize = 14f
                    setPadding(dp(14), dp(12), dp(14), dp(18))
                })
            } else {
                cookies.forEach { cookie ->
                    addView(TextView(activity).apply {
                        text = cookie
                        setTextColor(palette().text)
                        textSize = 12f
                        setTextIsSelectable(true)
                        setPadding(dp(14), dp(9), dp(14), dp(9))
                    })
                    addDivider()
                }
                addPrimaryButton("Действия с cookies") {
                    ResultDelivery.deliverText(
                        activity,
                        "Cookies",
                        raw,
                        ResultDelivery.defaultFileName("cookies-${currentHost()}", raw),
                        "text/plain"
                    )
                }
                addDangerButton("Удалить cookies домена") {
                    dialog.dismiss()
                    confirmClearCookies(cookies.size)
                }
            }
        }
    }

    private fun confirmClearCookies(count: Int) {
        if (count <= 0) {
            webViewController.clearCurrentDomainCookies()
            return
        }
        showBottomSheet("Удалить cookies?") { dialog ->
            addView(TextView(activity).apply {
                text = "Удалить $count cookies для ${currentHost()}?"
                setTextColor(palette().text)
                textSize = 14f
                setPadding(dp(14), dp(8), dp(14), dp(14))
            })
            addDangerButton("Удалить") {
                dialog.dismiss()
                webViewController.clearCurrentDomainCookies()
            }
        }
    }

    private fun showInterfaceMenu() {
        showBottomSheet("Интерфейс") { dialog ->
            addMenuRow(TechIconDrawable.Kind.THEME, "Тема", WebUiTheme.savedMode(activity).label) {
                dialog.dismiss()
                showThemePicker()
            }
            addMenuRow(TechIconDrawable.Kind.COLOR, "Цвет элементов", WebUiTheme.accentLabel(activity)) {
                dialog.dismiss()
                showAccentPicker()
            }
        }
    }

    private fun showThemePicker() {
        showBottomSheet("Тема") { dialog ->
            val current = WebUiTheme.savedMode(activity)
            WebUiTheme.Mode.entries.forEach { mode ->
                addMenuRow(TechIconDrawable.Kind.THEME, mode.label, if (mode == current) "Текущая тема" else "") {
                    WebUiTheme.save(activity, mode)
                    dialog.dismiss()
                }
            }
        }
    }

    private fun showAccentPicker() {
        showBottomSheet("Цвет элементов") { _ ->
            addView(TextView(activity).apply {
                text = "Водите пальцем по большой палитре для насыщенности и яркости, а по нижней цветной полосе — для оттенка. Изменение применяется сразу."
                setTextColor(palette().secondary)
                textSize = 12.5f
                setPadding(dp(14), dp(2), dp(14), dp(10))
            })

            val previewRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(2), dp(14), dp(8))
            }
            val preview = View(activity).apply {
                background = rounded(palette().accent, 12f, palette().divider)
            }
            val value = TextView(activity).apply {
                text = WebUiTheme.colorLabel(palette().accent)
                setTextColor(palette().text)
                textSize = 13f
                typeface = Typeface.MONOSPACE
                setPadding(dp(10), 0, 0, 0)
            }
            previewRow.addView(preview, LinearLayout.LayoutParams(dp(34), dp(34)))
            previewRow.addView(value, LinearLayout.LayoutParams(0, dp(34), 1f))
            addView(previewRow)

            val picker = AccentColorPickerView(activity).apply {
                setColor(palette().accent)
                onColorChanged = { color ->
                    onAccentColor(color, false)
                    updateAccent(color or 0xFF000000.toInt())
                    val current = palette()
                    preview.background = rounded(current.accent, 12f, current.divider)
                    value.text = WebUiTheme.colorLabel(current.accent)
                }
                onColorCommitted = { color ->
                    onAccentColor(color, true)
                    updateAccent(color or 0xFF000000.toInt())
                }
            }
            addView(picker, LinearLayout.LayoutParams(-1, dp(284)).apply {
                setMargins(dp(4), 0, dp(4), dp(6))
            })
        }
    }

    private fun showAbout() {
        val version = try {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "dev"
        } catch (_: Exception) {
            "dev"
        }
        showBottomSheet("web research") { _ ->
            addView(TextView(activity).apply {
                text = "Версия приложения: $version\nРелиз: $version\n\nМобильный браузер для исследования сетевого взаимодействия сайтов."
                setTextColor(palette().text)
                textSize = 14f
                setPadding(dp(14), dp(6), dp(14), dp(18))
            })
        }
    }

    private fun showBottomSheet(title: String, build: LinearLayout.(Dialog) -> Unit): Dialog {
        val dialog = Dialog(activity)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(true)
        activeSheetDialog = dialog

        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(12), dp(6), dp(16))
            background = rounded(palette().card, 22f, palette().divider)
        }
        val sheetHeader = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(2), dp(8), dp(8))
        }
        val closeButton = Button(activity).apply {
            text = ""
            contentDescription = "Закрыть"
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            background = rounded(palette().address, 14f, palette().divider)
            foreground = TechIconDrawable(TechIconDrawable.Kind.CLOSE, palette().accent)
            setOnClickListener { dialog.dismiss() }
        }
        activeSheetCloseButton = closeButton
        sheetHeader.addView(closeButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        sheetHeader.addView(TextView(activity).apply {
            text = title
            setTextColor(palette().text)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        panel.addView(sheetHeader)
        panel.build(dialog)

        var swipeStartY = 0f
        var swipeStartedAtTop = false
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            setBackgroundColor(Color.TRANSPARENT)
            addView(panel, ViewGroup.LayoutParams(-1, -2))
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        swipeStartY = event.rawY
                        swipeStartedAtTop = scrollY == 0
                        false
                    }
                    MotionEvent.ACTION_UP -> {
                        val closeBySwipe = swipeStartedAtTop && event.rawY - swipeStartY >= dp(72)
                        swipeStartedAtTop = false
                        if (closeBySwipe) {
                            dialog.dismiss()
                            true
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        swipeStartedAtTop = false
                        false
                    }
                    else -> false
                }
            }
        }
        dialog.setContentView(scroll)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setGravity(Gravity.BOTTOM)
            attributes = attributes.apply {
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                dimAmount = 0.35f
            }
        }
        dialog.setOnDismissListener {
            if (activeSheetDialog === dialog) {
                activeSheetDialog = null
                activeSheetCloseButton = null
            }
            if (activeBrowserMenu === dialog) activeBrowserMenu = null
        }
        dialog.show()
        return dialog
    }

    private fun LinearLayout.addSection(label: String) {
        addView(TextView(activity).apply {
            text = label
            setTextColor(palette().secondary)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = .08f
            setPadding(dp(14), dp(14), dp(14), dp(5))
        })
    }

    private fun LinearLayout.addMenuRow(
        icon: TechIconDrawable.Kind,
        title: String,
        subtitle: String = "",
        click: () -> Unit
    ) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(8), dp(6))
            isClickable = true
            isFocusable = true
            setOnClickListener { click() }
        }
        row.addView(ImageView(activity).apply {
            setImageDrawable(TechIconDrawable(icon, palette().accent))
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val labels = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        labels.addView(TextView(activity).apply {
            text = title
            setTextColor(palette().text)
            textSize = 14.5f
        })
        if (subtitle.isNotBlank()) labels.addView(TextView(activity).apply {
            text = subtitle
            setTextColor(palette().secondary)
            textSize = 11f
            maxLines = 1
        })
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(ImageView(activity).apply {
            setImageDrawable(TechIconDrawable(TechIconDrawable.Kind.CHEVRON_RIGHT, palette().secondary))
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(40), dp(48)))
        addView(row)
        addDivider()
    }

    private fun LinearLayout.addDivider() {
        addView(
            View(activity).apply { setBackgroundColor(palette().divider) },
            LinearLayout.LayoutParams(-1, dp(1)).apply {
                marginStart = dp(58)
                marginEnd = dp(10)
            }
        )
    }

    private fun LinearLayout.addPrimaryButton(label: String, click: () -> Unit) {
        addView(Button(activity).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WebUiTheme.contrastText(palette().accent))
            background = rounded(palette().accent, 15f)
            setOnClickListener { click() }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply {
            setMargins(dp(12), dp(12), dp(12), dp(2))
        })
    }

    private fun LinearLayout.addDangerButton(label: String, click: () -> Unit) {
        addView(Button(activity).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            setTextColor(palette().red)
            background = rounded(palette().address, 15f, palette().divider)
            setOnClickListener { click() }
        }, LinearLayout.LayoutParams(-1, dp(46)).apply {
            setMargins(dp(12), dp(8), dp(12), dp(2))
        })
    }

    private fun currentPage(): String = currentPageProvider()
    private fun currentHost(): String = hostOf(currentPage()).ifBlank { "Текущий сайт" }
    private fun hostOf(url: String): String = try { Uri.parse(url).host.orEmpty() } catch (_: Exception) { "" }
    private fun cookieCount(): Int =
        CookieManager.getInstance().getCookie(currentPage()).orEmpty()
            .split(';')
            .count { it.trim().isNotBlank() }

    private fun palette(): WebUiTheme.Palette = paletteProvider()
    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

    private fun rounded(fill: Int, radius: Float, stroke: Int = Color.TRANSPARENT): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radius.toInt()).toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }
}
