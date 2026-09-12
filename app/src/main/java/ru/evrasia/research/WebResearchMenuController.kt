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
import android.view.ViewConfiguration
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
    private var activeSheetPanel: LinearLayout? = null
    private var activeSheetBody: LinearLayout? = null
    private var activeSheetFooter: LinearLayout? = null
    private var activeSheetScroll: ScrollView? = null
    private var activeSheetTitle: TextView? = null
    private var activeSheetBackButton: Button? = null
    private var activeSheetCloseButton: Button? = null

    fun toggleBrowserMenu() {
        activeBrowserMenu?.takeIf { it.isShowing }?.let {
            it.dismiss()
            return
        }
        showMainMenuSheet()
    }

    private fun showMainMenuSheet() {
        val dialog = showBottomSheet("Меню", onBack = null) { sheet ->
            addSection("СТРАНИЦА")
            addSiteVersionRow(sheet)

            addSection("ДАННЫЕ САЙТА")
            val cookieCount = cookieCount()
            addMenuRow(TechIconDrawable.Kind.COOKIE, "Cookies", "${currentHost()} · $cookieCount cookies") {
                showCookiesSheet()
            }
            addMenuRow(TechIconDrawable.Kind.DELETE, "Удалить cookies домена", if (cookieCount > 0) "$cookieCount cookies" else "Нет cookies") {
                confirmClearCookies(cookieCount)
            }
            addMenuRow(TechIconDrawable.Kind.APPEARANCE, "Интерфейс", "Тема и цвет элементов") {
                showInterfaceMenu()
            }

            addSection("ПРИЛОЖЕНИЕ")
            addMenuRow(TechIconDrawable.Kind.INFO, "О приложении", "web research") {
                showAbout()
            }
        }
        activeBrowserMenu = dialog
    }

    fun updateAccent(color: Int) {
        activeSheetBackButton?.foreground = TechIconDrawable(TechIconDrawable.Kind.BACK, color)
        activeSheetCloseButton?.foreground = TechIconDrawable(TechIconDrawable.Kind.CLOSE, color)
    }

    fun dismiss() {
        activeSheetDialog?.dismiss()
        clearActiveSheet()
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
                activeSheetFooter?.apply {
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
                        confirmClearCookies(cookies.size) { showCookiesSheet() }
                    }
                }
            }
        }
    }

    private fun confirmClearCookies(
        count: Int,
        onBack: () -> Unit = { showMainMenuSheet() }
    ) {
        if (count <= 0) {
            webViewController.clearCurrentDomainCookies()
            return
        }
        showBottomSheet("Удалить cookies?", onBack = onBack) { dialog ->
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
                showThemePicker()
            }
            addMenuRow(TechIconDrawable.Kind.COLOR, "Цвет элементов", WebUiTheme.accentLabel(activity)) {
                showAccentPicker()
            }
        }
    }

    private fun showThemePicker() {
        showBottomSheet("Тема", onBack = { showInterfaceMenu() }) { dialog ->
            val current = WebUiTheme.savedMode(activity)
            WebUiTheme.Mode.entries.forEach { mode ->
                addMenuRow(TechIconDrawable.Kind.THEME, mode.label, if (mode == current) "Текущая тема" else "") {
                    applyThemeMode(dialog, current, mode)
                }
            }
        }
    }

    private fun applyThemeMode(
        dialog: Dialog,
        current: WebUiTheme.Mode,
        mode: WebUiTheme.Mode
    ) {
        activeSheetBody?.animate()?.cancel()
        activeSheetPanel?.animate()?.cancel()
        val decor = activity.window.decorView
        dialog.dismiss()
        clearActiveSheet()
        if (mode == current) return
        decor.post {
            if (!activity.isFinishing && !activity.isDestroyed) {
                WebUiTheme.save(activity, mode)
            }
        }
    }

    private fun showAccentPicker() {
        showBottomSheet("Цвет элементов", onBack = { showInterfaceMenu() }) { _ ->
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

    private fun showBottomSheet(
        title: String,
        onBack: (() -> Unit)? = { showMainMenuSheet() },
        build: LinearLayout.(Dialog) -> Unit
    ): Dialog {
        val currentDialog = activeSheetDialog
        val currentBody = activeSheetBody
        if (currentDialog != null && currentDialog.isShowing && currentBody != null) {
            transitionSheet(currentDialog, title, onBack, build)
            return currentDialog
        }

        val dialog = Dialog(activity)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(true)

        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(8), dp(6), dp(12))
            background = rounded(palette().card, 22f, palette().divider)
            alpha = 0f
        }
        val dragHandle = View(activity).apply {
            background = rounded(palette().secondary, 2f)
        }
        panel.addView(
            dragHandle,
            LinearLayout.LayoutParams(dp(38), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, dp(2), 0, dp(6))
            }
        )

        val sheetHeader = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), dp(6))
        }
        val backButton = Button(activity).apply {
            text = ""
            contentDescription = "Назад"
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            background = rounded(palette().address, 14f, palette().divider)
            foreground = TechIconDrawable(TechIconDrawable.Kind.BACK, palette().accent)
        }
        sheetHeader.addView(backButton, LinearLayout.LayoutParams(dp(48), dp(48)))

        val titleView = TextView(activity).apply {
            setTextColor(palette().text)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(10), 0)
        }
        sheetHeader.addView(titleView, LinearLayout.LayoutParams(0, dp(48), 1f))

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
        sheetHeader.addView(closeButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        panel.addView(sheetHeader)

        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop
        val scroll = SheetScrollView(
            activity,
            touchSlop,
            onDrag = { dy -> panel.translationY = dy.coerceAtLeast(0f) * 0.82f },
            onRelease = { dy, elapsed -> finishSwipeDismiss(panel, dialog, dy, elapsed) },
            onCancel = { panel.animate().translationY(0f).setDuration(120L).start() }
        ).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(Color.TRANSPARENT)
            addView(body, ViewGroup.LayoutParams(-1, -2))
        }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val footer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
        }
        panel.addView(footer, LinearLayout.LayoutParams(-1, -2))

        activeSheetDialog = dialog
        activeSheetPanel = panel
        activeSheetBody = body
        activeSheetFooter = footer
        activeSheetScroll = scroll
        activeSheetTitle = titleView
        activeSheetBackButton = backButton
        activeSheetCloseButton = closeButton

        renderSheet(dialog, title, onBack, build)
        installSwipeDismiss(sheetHeader, titleView, panel, dialog)

        dialog.setContentView(panel)
        val sheetHeight = (activity.resources.displayMetrics.heightPixels * 0.72f).toInt()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setGravity(Gravity.BOTTOM)
            setWindowAnimations(0)
            attributes = attributes.apply {
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = sheetHeight
                dimAmount = 0.35f
            }
        }
        dialog.setOnDismissListener {
            if (activeSheetDialog === dialog) clearActiveSheet()
        }
        dialog.show()
        panel.animate().alpha(1f).setDuration(140L).start()
        return dialog
    }

    private fun transitionSheet(
        dialog: Dialog,
        title: String,
        onBack: (() -> Unit)?,
        build: LinearLayout.(Dialog) -> Unit
    ) {
        val body = activeSheetBody ?: return
        val footer = activeSheetFooter
        body.animate().cancel()
        footer?.animate()?.cancel()
        footer?.animate()?.alpha(0f)?.setDuration(90L)?.start()
        body.animate()
            .alpha(0f)
            .setDuration(90L)
            .withEndAction {
                renderSheet(dialog, title, onBack, build)
                body.alpha = 0f
                footer?.alpha = 0f
                body.animate().alpha(1f).setDuration(140L).start()
                footer?.animate()?.alpha(1f)?.setDuration(140L)?.start()
            }
            .start()
    }

    private fun renderSheet(
        dialog: Dialog,
        title: String,
        onBack: (() -> Unit)?,
        build: LinearLayout.(Dialog) -> Unit
    ) {
        activeSheetTitle?.text = title
        activeSheetBackButton?.apply {
            visibility = if (onBack == null) View.INVISIBLE else View.VISIBLE
            setOnClickListener { onBack?.invoke() }
        }
        activeSheetFooter?.removeAllViews()
        activeSheetBody?.apply {
            removeAllViews()
            build(dialog)
        }
        activeSheetScroll?.scrollTo(0, 0)
    }

    private fun installSwipeDismiss(
        header: View,
        title: View,
        panel: View,
        dialog: Dialog
    ) {
        val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop
        var startY = 0f
        var startTime = 0L
        var dragging = false

        val listener = View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    startTime = event.eventTime
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - startY
                    if (dy > touchSlop) dragging = true
                    if (dragging) panel.translationY = dy.coerceAtLeast(0f) * 0.82f
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        val dy = (event.rawY - startY).coerceAtLeast(0f)
                        val elapsed = (event.eventTime - startTime).coerceAtLeast(1L)
                        finishSwipeDismiss(panel, dialog, dy, elapsed)
                    }
                    dragging = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) panel.animate().translationY(0f).setDuration(120L).start()
                    dragging = false
                    true
                }
                else -> true
            }
        }
        header.setOnTouchListener(listener)
        title.setOnTouchListener(listener)
    }

    private fun finishSwipeDismiss(panel: View, dialog: Dialog, dy: Float, elapsed: Long) {
        val velocity = dy * 1000f / elapsed.coerceAtLeast(1L)
        val close = dy >= panel.height * 0.18f || velocity >= dp(900)
        if (close) {
            panel.animate()
                .translationY(panel.height.toFloat())
                .setDuration(160L)
                .withEndAction { dialog.dismiss() }
                .start()
        } else {
            panel.animate().translationY(0f).setDuration(120L).start()
        }
    }

    private fun clearActiveSheet() {
        activeBrowserMenu = null
        activeSheetDialog = null
        activeSheetPanel = null
        activeSheetBody = null
        activeSheetFooter = null
        activeSheetScroll = null
        activeSheetTitle = null
        activeSheetBackButton = null
        activeSheetCloseButton = null
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

    private class SheetScrollView(
        context: android.content.Context,
        private val touchSlop: Int,
        private val onDrag: (Float) -> Unit,
        private val onRelease: (Float, Long) -> Unit,
        private val onCancel: () -> Unit
    ) : ScrollView(context) {
        private var startX = 0f
        private var startY = 0f
        private var startTime = 0L
        private var dragEligible = false
        private var dragging = false

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> rememberDown(event)
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && shouldStartDrag(event)) {
                        dragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                    if (dragging) return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) return true
            }
            return super.onInterceptTouchEvent(event)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> rememberDown(event)
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && shouldStartDrag(event)) {
                        dragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    if (dragging) {
                        onDrag((event.rawY - startY).coerceAtLeast(0f))
                        return true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        val dy = (event.rawY - startY).coerceAtLeast(0f)
                        val elapsed = (event.eventTime - startTime).coerceAtLeast(1L)
                        dragging = false
                        onRelease(dy, elapsed)
                        return true
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        dragging = false
                        onCancel()
                        return true
                    }
                }
            }
            return super.onTouchEvent(event)
        }

        private fun rememberDown(event: MotionEvent) {
            startX = event.rawX
            startY = event.rawY
            startTime = event.eventTime
            dragEligible = scrollY == 0
            dragging = false
        }

        private fun shouldStartDrag(event: MotionEvent): Boolean {
            if (!dragEligible) return false
            val dx = event.rawX - startX
            val dy = event.rawY - startY
            return dy > touchSlop && kotlin.math.abs(dy) > kotlin.math.abs(dx)
        }
    }

}
