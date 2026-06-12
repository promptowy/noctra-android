package com.promptowy.noctra

import android.content.Context
import com.google.gson.Gson
import java.io.File

data class AppSettings(
    val searchEngine: String = "duckduckgo",
    val homepage: String = "https://duckduckgo.com",
    val startup: String = "restore",
    val shieldOffSites: MutableList<String> = mutableListOf()
)

class SettingsManager(context: Context) {
    private val file = File(context.filesDir, "settings.json")
    private val gson = Gson()
    var settings: AppSettings = load()

    private fun load(): AppSettings = try {
        gson.fromJson(file.readText(), AppSettings::class.java) ?: AppSettings()
    } catch (_: Exception) { AppSettings() }

    fun save() { file.writeText(gson.toJson(settings)) }

    val searchUrl: String get() = when (settings.searchEngine) {
        "brave"  -> "https://search.brave.com/search?q="
        "google" -> "https://www.google.com/search?q="
        else     -> "https://duckduckgo.com/?q="
    }

    fun isShieldOff(host: String?): Boolean = host != null && settings.shieldOffSites.contains(host)

    fun toggleShield(host: String) {
        if (settings.shieldOffSites.contains(host)) settings.shieldOffSites.remove(host)
        else settings.shieldOffSites.add(host)
        save()
    }

    fun toUrl(input: String): String {
        val text = input.trim()
        if (text.startsWith("http://") || text.startsWith("https://") || text.startsWith("file://")) return text
        if (Regex("""^[\w-]+(\.[\w-]+)+(:\d+)?(/.*)?$""").matches(text)) return "https://$text"
        return searchUrl + java.net.URLEncoder.encode(text, "UTF-8")
    }
}
