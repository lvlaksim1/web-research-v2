package ru.evrasia.research

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

internal object WebResearchBrowserLayout {
    data class Callbacks(
        val onMenu: () -> Unit,
        val onAddressGo: () -> Unit,
        val onAddressFocusChanged: (Boolean) -> Unit,
        val onAddressChanged: () -> Unit,
        val onPageAction: () -> Unit,
        val onBookmarkAdd: () -> Unit,
        val onZip: () -> Unit,
        val onNetwork: () -> Unit
    )

    data class Views(
        val root: LinearLayout,
        val web: WebView,
        val swipeRefresh: SwipeRefreshLayout,
        val address: EditText,
        val pageAction: Button,
        val bookmarkBar: LinearLayout,
        val bookmarkSpinner: Spinner,
        val bookmarkAddButton: Button,
        val zipButton: Button,
        val zipRecordingIndicator: View,
        val menuButton: Button,
        val networkButton: Button,
        val networkBadge: TextView,
        val progress: ProgressBar
    )

    fun create(
        activity: AppCompatActivity,
        palette: WebUiTheme.Palette,
        handler: Handler,
        callbacks: Callbacks
    ): Views {
        fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
        fun rounded(fill: Int, radius: Float, stroke: Int = Color.TRANSPARENT): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(fill)
                cornerRadius = dp(radius.toInt()).toFloat()
                if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
            }
        fun iconButton(
            kind: TechIconDrawable.Kind,
            description: String,
            strong: Boolean,
            click: () -> Unit
        ): Button =
            Button(activity).apply {
                text = ""
                contentDescription = description
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setPadding(0, 0, 0, 0)
                background = rounded(
                    if (strong) palette.card else Color.TRANSPARENT,
                    16f,
                    if (strong) palette.divider else Color.TRANSPARENT
                )
                foreground = TechIconDrawable(kind, palette.accent)
                setOnClickListener { click() }
            }

        val root = LinearLayout(activity).apply {
            tag = "web-research-root"
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.background)
        }

        val toolbar = LinearLayout(activity).apply {
            tag = "browser-toolbar"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), dp(6), dp(7), dp(6))
            setBackgroundColor(palette.background)
            clipChildren = true
            clipToPadding = true
        }

        val menuButton = iconButton(TechIconDrawable.Kind.MENU, "Меню", false, callbacks.onMenu)
        toolbar.addView(menuButton, LinearLayout.LayoutParams(dp(48), dp(48)))

        lateinit var bookmarkBar: LinearLayout
        val address = EditText(activity).apply {
            tag = "browser-address"
            hint = "Адрес сайта"
            setHintTextColor(palette.secondary)
            setTextColor(palette.text)
            setSingleLine(true)
            textSize = 14f
            imeOptions = EditorInfo.IME_ACTION_GO
            background = rounded(palette.address, 22f)
            setPadding(dp(14), 0, dp(14), 0)
            setText("https://evrasia.rest/")
            setSelectAllOnFocus(true)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    callbacks.onAddressGo()
                    true
                } else {
                    false
                }
            }
            onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                if (hasFocus) bookmarkBar.visibility = View.VISIBLE
                callbacks.onAddressFocusChanged(hasFocus)
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    callbacks.onAddressChanged()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        toolbar.addView(address, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(4) })

        val pageAction = iconButton(
            TechIconDrawable.Kind.NAVIGATE,
            "Перейти",
            true,
            callbacks.onPageAction
        ).apply {
            tag = "browser-page-action"
        }
        toolbar.addView(pageAction, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(5) })

        val zipContainer = FrameLayout(activity).apply {
            clipChildren = false
            clipToPadding = false
        }
        val zipButton = Button(activity).apply {
            tag = "browser-zip"
            text = "ZIP"
            contentDescription = "Начать запись ZIP"
            isAllCaps = false
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = .08f
            gravity = Gravity.CENTER
            setTextColor(palette.accent)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            background = rounded(palette.card, 16f, palette.divider)
            setOnClickListener { callbacks.onZip() }
        }
        zipContainer.addView(zipButton, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
        val zipRecordingIndicator = View(activity).apply {
            tag = "browser-zip-recording"
            visibility = View.GONE
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(224, 67, 67))
            }
        }
        zipContainer.addView(
            zipRecordingIndicator,
            FrameLayout.LayoutParams(dp(10), dp(10), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(3)
                marginEnd = dp(3)
            }
        )
        toolbar.addView(zipContainer, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(4) })

        val networkContainer = FrameLayout(activity).apply {
            tag = "browser-network"
            clipChildren = true
            clipToPadding = true
        }
        val networkButton = iconButton(TechIconDrawable.Kind.NETWORK, "Network / Research", true, callbacks.onNetwork)
        networkContainer.addView(networkButton, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
        val networkBadge = TextView(activity).apply {
            tag = "network-badge"
            visibility = View.GONE
            setTextColor(WebUiTheme.contrastText(palette.accent))
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minWidth = dp(17)
            maxLines = 1
            setPadding(dp(3), 0, dp(3), 0)
            background = rounded(palette.accent, 9f)
        }
        networkContainer.addView(
            networkBadge,
            FrameLayout.LayoutParams(-2, dp(17), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(3)
                marginEnd = dp(3)
            }
        )
        toolbar.addView(networkContainer, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(4) })
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(60)))

        val bookmarkSpinner = Spinner(activity).apply {
            tag = "browser-bookmarks"
            contentDescription = "Закладки"
            background = rounded(palette.address, 14f, palette.divider)
        }
        val bookmarkAddButton = iconButton(
            TechIconDrawable.Kind.BOOKMARK_ADD,
            "Добавить текущий адрес в закладки",
            true,
            callbacks.onBookmarkAdd
        )
        bookmarkBar = LinearLayout(activity).apply {
            tag = "browser-address-tools"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(59), dp(2), dp(7), dp(4))
            setBackgroundColor(palette.background)
            addView(bookmarkSpinner, LinearLayout.LayoutParams(0, dp(44), 1f))
            addView(bookmarkAddButton, LinearLayout.LayoutParams(dp(48), dp(44)).apply { marginStart = dp(5) })
        }
        root.addView(bookmarkBar, LinearLayout.LayoutParams(-1, dp(50)))

        val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            tag = "browser-progress"
            max = 100
            progressTintList = ColorStateList.valueOf(palette.accent)
            progressBackgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            visibility = View.INVISIBLE
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(2)))

        val web = WebView(activity).apply {
            tag = "browser-webview"
            setBackgroundColor(Color.WHITE)
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN && bookmarkBar.visibility == View.VISIBLE) {
                    bookmarkBar.visibility = View.GONE
                    address.clearFocus()
                }
                false
            }
        }
        lateinit var swipeRefresh: SwipeRefreshLayout
        swipeRefresh = SwipeRefreshLayout(activity).apply {
            tag = "browser-webview-container"
            setColorSchemeColors(palette.accent)
            setProgressBackgroundColorSchemeColor(palette.card)
            setOnChildScrollUpCallback { _, _ -> web.canScrollVertically(-1) }
            setOnRefreshListener {
                web.reload()
                handler.postDelayed({ swipeRefresh.isRefreshing = false }, 15000)
            }
            addView(web, ViewGroup.LayoutParams(-1, -1))
        }
        root.addView(swipeRefresh, LinearLayout.LayoutParams(-1, 0, 1f))

        return Views(
            root = root,
            web = web,
            swipeRefresh = swipeRefresh,
            address = address,
            pageAction = pageAction,
            bookmarkBar = bookmarkBar,
            bookmarkSpinner = bookmarkSpinner,
            bookmarkAddButton = bookmarkAddButton,
            zipButton = zipButton,
            zipRecordingIndicator = zipRecordingIndicator,
            menuButton = menuButton,
            networkButton = networkButton,
            networkBadge = networkBadge,
            progress = progress
        )
    }

    fun applyAccent(
        activity: AppCompatActivity,
        views: Views,
        palette: WebUiTheme.Palette
    ) {
        val accent = palette.accent
        fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
        fun rounded(fill: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radius.toInt()).toFloat()
        }
        views.zipButton.setTextColor(accent)
        views.bookmarkAddButton.foreground = TechIconDrawable(TechIconDrawable.Kind.BOOKMARK_ADD, accent)
        views.menuButton.foreground = TechIconDrawable(TechIconDrawable.Kind.MENU, accent)
        val pageKind = when (views.pageAction.contentDescription?.toString()) {
            "Остановить загрузку" -> TechIconDrawable.Kind.STOP
            "Обновить" -> TechIconDrawable.Kind.RELOAD
            else -> TechIconDrawable.Kind.NAVIGATE
        }
        views.pageAction.foreground = TechIconDrawable(pageKind, accent)
        views.networkButton.foreground = TechIconDrawable(TechIconDrawable.Kind.NETWORK, accent)
        views.networkBadge.setTextColor(WebUiTheme.contrastText(accent))
        views.networkBadge.background = rounded(accent, 9f)
        views.progress.progressTintList = ColorStateList.valueOf(accent)
        views.swipeRefresh.setColorSchemeColors(accent)
    }
}
