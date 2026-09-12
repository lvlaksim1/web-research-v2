package ru.evrasia.research

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.json.JSONObject
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean

class WebResearchV10Activity : AppCompatActivity() {
    internal fun researchWebView(): WebView? = if (::web.isInitialized) web else null
    internal fun researchArchive(): ResearchArchive = archive
    internal fun researchUserAgent(): String = if (::userAgent.isInitialized) userAgent else ""
    internal fun clearResearchSession() {
        archive.clear()
        NetworkDebugStore.clear()
        if (::captureController.isInitialized) captureController.clearPending()
        updateBadge()
    }

    private lateinit var palette: WebUiTheme.Palette
    private lateinit var browserViews: WebResearchBrowserLayout.Views
    private lateinit var web: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var address: EditText
    private lateinit var pageAction: Button
    private lateinit var zipButton: Button
    private lateinit var menuButton: Button
    private lateinit var networkButton: Button
    private lateinit var networkBadge: TextView
    private lateinit var progress: ProgressBar
    private lateinit var navigationController: WebNavigationController
    private lateinit var bookmarkController: WebBookmarkController
    private lateinit var webViewController: WebResearchWebViewController
    private lateinit var exportController: WebResearchExportController
    private lateinit var captureController: WebCaptureController
    private lateinit var menuController: WebResearchMenuController
    private val archive = ResearchArchive()
    private lateinit var userAgent: String
    private val badgeUpdatePending = AtomicBoolean(false)
    private val uiHandler = Handler(Looper.getMainLooper())
    private var loading = false
    private var editingAddress = false
    private var bookmarkBarVisible = false
    private var zipRecordingStartedAt: Long? = null

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        WebUiTheme.applySaved(this)
        super.onCreate(savedInstanceState)
        title = "web research"
        palette = WebUiTheme.palette(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        configureSystemBars()

        browserViews = WebResearchBrowserLayout.create(
            activity = this,
            palette = palette,
            handler = uiHandler,
            callbacks = WebResearchBrowserLayout.Callbacks(
                onMenu = { if (::menuController.isInitialized) menuController.toggleBrowserMenu() },
                onAddressGo = { navigateFromAddress() },
                onAddressFocusChanged = { hasFocus ->
                    editingAddress = hasFocus
                    setBookmarkBarVisible(hasFocus)
                    updatePageAction()
                },
                onAddressFocusedTap = {
                    setBookmarkBarVisible(!bookmarkBarVisible)
                },
                onAddressToolsDismiss = {
                    if (::address.isInitialized) address.clearFocus()
                    setBookmarkBarVisible(false)
                },
                onAddressChanged = {
                    if (::address.isInitialized && address.hasFocus()) editingAddress = true
                    updatePageAction()
                },
                onPageAction = { handlePageAction() },
                onBookmarkAdd = {
                    if (::bookmarkController.isInitialized && ::address.isInitialized) {
                        bookmarkController.save(address.text.toString())
                    }
                },
                onZip = { handleZipAction() },
                onNetwork = {
                    ensureInstrumentation()
                    startActivity(Intent(this, NetworkDebuggerActivity::class.java))
                }
            )
        )
        web = browserViews.web
        swipeRefresh = browserViews.swipeRefresh
        address = browserViews.address
        pageAction = browserViews.pageAction
        zipButton = browserViews.zipButton
        menuButton = browserViews.menuButton
        networkButton = browserViews.networkButton
        networkBadge = browserViews.networkBadge
        progress = browserViews.progress
        val root = browserViews.root
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.databaseEnabled = true
        web.settings.setSupportMultipleWindows(true)
        web.settings.javaScriptCanOpenWindowsAutomatically = true
        userAgent = web.settings.userAgentString + " WebResearch/10"
        web.settings.userAgentString = userAgent

        navigationController = WebNavigationController(this, web, address) { addRecord(it) }
        bookmarkController = WebBookmarkController(
            activity = this,
            normalizeUrl = { raw -> navigationController.normalizeUrl(raw) },
            onOpen = { url ->
                setBookmarkBarVisible(false)
                address.clearFocus()
                navigationController.navigate(url)
            }
        )
        bookmarkController.bind(browserViews.bookmarkSpinner)
        captureController = WebCaptureController(
            activity = this,
            web = web,
            archive = archive,
            userAgent = userAgent,
            record = { addRecord(it) },
            onChanged = { scheduleBadgeUpdate() },
            onSnapshot = { scheduleBadgeUpdate() }
        )
        WebView.setWebContentsDebuggingEnabled(true)
        web.addJavascriptInterface(captureController.bridge, "EvrasiaResearch")
        exportController = WebResearchExportController(
            activity = this,
            archive = archive,
            web = web,
            captureSnapshot = { capturePageSnapshot() }
        )

        webViewController = WebResearchWebViewController(
            activity = this,
            web = web,
            swipeRefresh = swipeRefresh,
            address = address,
            captureController = captureController,
            navigationController = navigationController,
            handler = uiHandler,
            record = { addRecord(it) },
            onLoadingChanged = { isLoading ->
                loading = isLoading
                if (!isLoading) editingAddress = false
                updatePageAction()
                progress.visibility = if (isLoading) View.VISIBLE else View.INVISIBLE
            },
            onProgressChanged = { value ->
                progress.progress = value
                if (value in 1..99) progress.visibility = View.VISIBLE
                if (value >= 100 && !loading) progress.visibility = View.INVISIBLE
            },
            onPageUrlChanged = { url ->
                if (!address.hasFocus()) address.setText(url)
            }
        )
        webViewController.install()
        menuController = WebResearchMenuController(
            activity = this,
            bookmarkController = bookmarkController,
            webViewController = webViewController,
            paletteProvider = { palette },
            currentPageProvider = { currentPage() },
            onAccentColor = { color, persist -> applyAccentColor(color, persist) }
        )
        navigationController.navigate("https://evrasia.rest/")
    }

    private fun configureSystemBars() {
        window.statusBarColor = palette.background
        window.navigationBarColor = palette.background
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !palette.dark
            isAppearanceLightNavigationBars = !palette.dark
        }
    }

    private fun handlePageAction() {
        when {
            loading -> web.stopLoading()
            editingAddress || address.hasFocus() -> navigateFromAddress()
            else -> web.reload()
        }
    }

    private fun navigateFromAddress() {
        navigationController.navigate(address.text.toString())
        editingAddress = false
        setBookmarkBarVisible(false)
        address.clearFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(address.windowToken, 0)
        updatePageAction()
    }

    private fun setBookmarkBarVisible(visible: Boolean) {
        if (!::browserViews.isInitialized || bookmarkBarVisible == visible) return
        bookmarkBarVisible = visible
        WebResearchBrowserLayout.setBookmarkBarVisible(this, browserViews, visible)
    }

    private fun updatePageAction() {
        if (!::pageAction.isInitialized) return
        val (kind, description) = when {
            loading -> TechIconDrawable.Kind.STOP to "Остановить загрузку"
            editingAddress || address.hasFocus() -> TechIconDrawable.Kind.NAVIGATE to "Перейти"
            else -> TechIconDrawable.Kind.RELOAD to "Обновить"
        }
        pageAction.text = ""
        pageAction.contentDescription = description
        pageAction.foreground = TechIconDrawable(kind, palette.accent)
    }

    private fun applyAccentColor(color: Int, persist: Boolean) {
        val opaque = color or 0xFF000000.toInt()
        if (persist) WebUiTheme.saveAccentColor(this, opaque)
        palette = palette.copy(accent = opaque)
        if (::browserViews.isInitialized) WebResearchBrowserLayout.applyAccent(this, browserViews, palette)
        if (::menuController.isInitialized) menuController.updateAccent(opaque)
    }

    private fun currentPage(): String = web.url ?: address.text?.toString().orEmpty()

    fun requestResourceCopy(url: String, headersJson: JSONObject?): Boolean =
        if (::captureController.isInitialized) captureController.requestResourceCopy(url, headersJson) else false

    fun ensureInstrumentation() {
        if (::captureController.isInitialized) captureController.ensureInstrumentation()
    }

    private fun capturePageSnapshot() {
        if (::captureController.isInitialized) captureController.capturePageSnapshot()
    }

    private fun addRecord(record: JSONObject) {
        archive.addRecord(record)
        scheduleBadgeUpdate()
    }

    private fun scheduleBadgeUpdate() {
        if (!badgeUpdatePending.compareAndSet(false, true)) return
        uiHandler.postDelayed({
            badgeUpdatePending.set(false)
            if (::networkBadge.isInitialized && !isFinishing) updateBadge()
        }, 250)
    }

    private fun updateBadge() {
        val count = archive.records.length()
        networkBadge.text = if (count > 99) "99+" else count.toString()
        networkBadge.visibility = if (count > 0) View.VISIBLE else View.GONE
    }

    private fun handleZipAction() {
        if (!::exportController.isInitialized || !::browserViews.isInitialized) return
        val startedAt = zipRecordingStartedAt
        if (startedAt == null) {
            zipRecordingStartedAt = System.currentTimeMillis()
            captureEnvironment(zipRecordingStartedAt!!)
            capturePageSnapshot()
            addRecord(
                JSONObject()
                    .put("source", "checkpoint")
                    .put("time", zipRecordingStartedAt!!)
                    .put("label", "before")
                    .put("reason", "recording_start")
                    .put("url", currentPage())
            )
            WebResearchBrowserLayout.setZipRecording(this, browserViews, true)
            zipButton.contentDescription = "Остановить запись ZIP"
        } else {
            val endedAt = System.currentTimeMillis()
            zipRecordingStartedAt = null
            WebResearchBrowserLayout.setZipRecording(this, browserViews, false)
            zipButton.contentDescription = "Начать запись ZIP"
            exportController.exportWindow(startedAt, endedAt)
        }
    }

    private fun captureEnvironment(startedAt: Long) {
        val metrics = resources.displayMetrics
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val webViewPackage = WebView.getCurrentWebViewPackage()
        val environment = JSONObject()
            .put("captureFormatVersion", 3)
            .put("recordingStartedAt", startedAt)
            .put("appVersion", packageInfo.versionName ?: "")
            .put("versionCode", packageInfo.longVersionCode)
            .put("androidVersion", Build.VERSION.RELEASE ?: "")
            .put("apiLevel", Build.VERSION.SDK_INT)
            .put("manufacturer", Build.MANUFACTURER ?: "")
            .put("model", Build.MODEL ?: "")
            .put("webViewPackage", webViewPackage?.packageName ?: "")
            .put("webViewVersion", webViewPackage?.versionName ?: "")
            .put("userAgent", web.settings.userAgentString ?: "")
            .put("widthPixels", metrics.widthPixels)
            .put("heightPixels", metrics.heightPixels)
            .put("density", metrics.density)
            .put("locale", Locale.getDefault().toLanguageTag())
            .put("timezone", TimeZone.getDefault().id)
        archive.putArtifact("environment.json", environment.toString(2).toByteArray(Charsets.UTF_8))
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (::exportController.isInitialized) exportController.handleResult(requestCode, resultCode, data)
    }

    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) {
            addRecord(
                JSONObject()
                    .put("source", "android-lifecycle")
                    .put("time", System.currentTimeMillis())
                    .put("event", "onResume")
                    .put("url", currentPage())
            )
        }
        if (::palette.isInitialized) {
            val savedAccent = WebUiTheme.savedAccentColor(this)
            if (savedAccent != palette.accent) applyAccentColor(savedAccent, persist = false)
        }
    }

    override fun onPause() {
        if (::web.isInitialized) {
            addRecord(
                JSONObject()
                    .put("source", "android-lifecycle")
                    .put("time", System.currentTimeMillis())
                    .put("event", "onPause")
                    .put("url", currentPage())
            )
        }
        super.onPause()
    }

    override fun onStop() {
        if (::web.isInitialized) {
            addRecord(
                JSONObject()
                    .put("source", "android-lifecycle")
                    .put("time", System.currentTimeMillis())
                    .put("event", "onStop")
                    .put("url", currentPage())
            )
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (::menuController.isInitialized) menuController.dismiss()
        if (::captureController.isInitialized) captureController.shutdown()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
