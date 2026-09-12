package ru.evrasia.research

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

internal class WebBookmarkController(
    private val activity: AppCompatActivity,
    private val normalizeUrl: (String) -> String,
    private val onOpen: (String) -> Unit
) {
    private val bookmarks = mutableListOf<String>()
    private val spinnerItems = mutableListOf<String>()
    private var spinner: Spinner? = null
    private var adapter: ArrayAdapter<String>? = null
    private var selectionArmed = false

    init {
        load()
    }

    fun all(): List<String> = bookmarks.toList()

    fun open(url: String) {
        if (url.isNotBlank()) onOpen(url)
    }

    fun save(raw: String) {
        val url = normalizeUrl(raw)
        if (!bookmarks.contains(url)) bookmarks.add(url)
        bookmarks.sort()
        persist()
        refreshSpinner()
        Toast.makeText(activity, "Закладка сохранена", Toast.LENGTH_SHORT).show()
    }

    fun delete(url: String) {
        if (bookmarks.remove(url)) {
            persist()
            refreshSpinner()
        }
    }

    fun bind(target: Spinner) {
        spinner = target
        adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, spinnerItems).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        target.adapter = adapter
        target.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                selectionArmed = true
                target.postDelayed({ selectionArmed = false }, 5000L)
            }
            false
        }
        target.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!selectionArmed || position <= 0 || position >= spinnerItems.size) return
                val url = spinnerItems[position]
                selectionArmed = false
                open(url)
                target.post { target.setSelection(0, false) }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        refreshSpinner()
    }

    private fun refreshSpinner() {
        spinnerItems.clear()
        spinnerItems.add("Закладки")
        spinnerItems.addAll(bookmarks)
        adapter?.notifyDataSetChanged()
        spinner?.setSelection(0, false)
    }

    fun openSelected() {
        val current = spinner ?: return
        if (bookmarks.isEmpty()) return
        val index = current.selectedItemPosition - 1
        if (index in bookmarks.indices) open(bookmarks[index])
    }

    fun deleteSelected() {
        val current = spinner ?: return
        if (bookmarks.isEmpty()) return
        val index = current.selectedItemPosition - 1
        if (index in bookmarks.indices) delete(bookmarks[index])
    }

    private fun load() {
        bookmarks.clear()
        val saved = activity.getSharedPreferences("web-research", Context.MODE_PRIVATE).getStringSet("bookmarks", emptySet()) ?: emptySet()
        bookmarks.addAll(saved.sorted())
    }

    private fun persist() {
        activity.getSharedPreferences("web-research", Context.MODE_PRIVATE).edit().putStringSet("bookmarks", bookmarks.toSet()).apply()
    }
}
