package ru.evrasia.research

import android.content.Intent
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class WebResearchExportController(
    private val activity: AppCompatActivity,
    private val archive: ResearchArchive,
    private val web: WebView,
    private val captureSnapshot: () -> Unit
) {
    fun exportWindow(startedAt: Long, endedAt: Long) {
        captureSnapshot()
        web.postDelayed({
            if (activity.isFinishing || activity.isDestroyed) return@postDelayed
            val selectedArchive = archive.snapshotWindow(startedAt, endedAt)
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            ResultDelivery.deliverGeneratedFile(activity, "Экспорт ZIP", "web-research-$stamp.zip", "application/zip") { output ->
                ResearchArchiveExporter(selectedArchive).writeZip(output, web.url ?: "")
            }
        }, 250L)
    }

    fun handleResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean =
        ResultDelivery.handleActivityResult(activity, requestCode, resultCode, data)
}
