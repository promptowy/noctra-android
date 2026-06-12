package com.promptowy.noctra

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class Bookmark(val title: String, val url: String, val date: Long = System.currentTimeMillis())

class BookmarkManager(context: Context) {
    private val file = File(context.filesDir, "bookmarks.json")
    private val gson = Gson()
    val bookmarks: MutableList<Bookmark> = load()

    private fun load(): MutableList<Bookmark> = try {
        val type = object : TypeToken<MutableList<Bookmark>>() {}.type
        gson.fromJson<MutableList<Bookmark>>(file.readText(), type) ?: mutableListOf()
    } catch (_: Exception) { mutableListOf() }

    private fun save() { file.writeText(gson.toJson(bookmarks)) }

    fun isBookmarked(url: String) = bookmarks.any { it.url == url }

    fun add(title: String, url: String) {
        if (!isBookmarked(url)) {
            bookmarks.add(0, Bookmark(title.ifBlank { url }, url))
            save()
        }
    }

    fun remove(url: String) {
        bookmarks.removeAll { it.url == url }
        save()
    }
}
