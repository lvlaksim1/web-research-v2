package ru.evrasia.research

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONObject

class NetworkDebuggerActivity : AppCompatActivity() {
    private val screenBackground get() = WebUiTheme.palette(this).background

    private lateinit var list: ListView
    private lateinit var adapter: NetworkDebuggerEventAdapter
    private lateinit var controlsController: NetworkDebuggerControlsController

    private val dataSource = NetworkDebuggerDataSource()
    private val allItems = mutableListOf<JSONObject>()
    private val items = mutableListOf<JSONObject>()
    private val changedIds = hashSetOf<Long>()
    private val handler = Handler(Looper.getMainLooper())
    private var lastRevision = -1L

    private val typeFilters = listOf("ALL", "JSON", "HTML", "JS", "CSS", "IMG", "PDF", "TEXT", "BIN", "OTHER")
    private val methodFilters = listOf("ALL", "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD", "WS", "SSE", "OTHER")
    private val detailsController by lazy { NetworkDebuggerDetailsController(this, changedIds) }
    private val realtimeController by lazy { NetworkDebuggerRealtimeController(this) }

    private val refresh = object : Runnable {
        override fun run() {
            refreshIncremental()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WebUiTheme.applySaved(this)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = screenBackground
        window.navigationBarColor = screenBackground
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !WebUiTheme.palette(this@NetworkDebuggerActivity).dark
            isAppearanceLightNavigationBars = !WebUiTheme.palette(this@NetworkDebuggerActivity).dark
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(screenBackground)
        }

        list = ListView(this).apply {
            divider = null
            dividerHeight = dp(2)
            setBackgroundColor(screenBackground)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            clipToPadding = false
        }
        adapter = NetworkDebuggerEventAdapter(this, items, changedIds) {
            controlsController.filters().mergeMode
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            val event = items[position]
            when {
                NetworkEventClassifier.isActionEvent(event) -> Unit
                NetworkEventClassifier.isRealtimeSession(event) -> realtimeController.show(event)
                else -> detailsController.show(event, controlsController.filters().query)
            }
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))

        controlsController = NetworkDebuggerControlsController(
            activity = this,
            typeFilters = typeFilters,
            methodFilters = methodFilters,
            onBack = { finish() },
            onRecordingToggle = {
                NetworkDebugStore.recording = !NetworkDebugStore.recording
                controlsController.updateRecording(NetworkDebugStore.recording)
            },
            onClear = { clearLogData() },
            onChanged = { applyFilters() }
        )
        root.addView(
            controlsController.createBar(NetworkDebugStore.recording),
            LinearLayout.LayoutParams(-1, dp(55))
        )

        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        refreshIncremental(force = true)
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun clearLogData() {
        NetworkDebugStore.clear()
        refreshIncremental(force = true)
        Toast.makeText(this, "Журнал очищен", Toast.LENGTH_SHORT).show()
    }

    private fun refreshIncremental(force: Boolean = false) {
        val result = dataSource.refresh(force, lastRevision)
        if (!result.changed) return
        lastRevision = result.revision
        allItems.clear()
        allItems.addAll(result.events)
        rebuildDynamicFilters()
        applyFilters()
    }

    private fun rebuildDynamicFilters() {
        controlsController.setDomains(
            allItems
                .mapNotNull { NetworkEventClassifier.hostOf(NetworkEventClassifier.eventLocation(it)) }
                .distinct()
                .sorted()
        )
    }

    private fun applyFilters() {
        if (!::controlsController.isInitialized) return
        val filters = controlsController.filters()
        val projection = NetworkDebuggerProjection.build(
            allItems,
            filters.mergeMode,
            filters.domain,
            filters.type,
            filters.method,
            filters.query,
            methodFilters
        )
        changedIds.clear()
        changedIds.addAll(projection.changedIds)
        items.clear()
        items.addAll(projection.rows)
        adapter.notifyDataSetChanged()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        ResultDelivery.handleActivityResult(this, requestCode, resultCode, data)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
