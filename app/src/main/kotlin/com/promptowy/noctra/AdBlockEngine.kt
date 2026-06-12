package com.promptowy.noctra

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

class AdBlockEngine(private val context: Context) {

    private val blockedDomains = HashSet<String>(150_000)
    @Volatile private var ready = false

    suspend fun load() = withContext(Dispatchers.IO) {
        try {
            context.assets.open("blocklist.txt").bufferedReader().use { reader ->
                reader.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith('#') }
                    .forEach { blockedDomains.add(it) }
            }
            ready = true
        } catch (_: Exception) {}
    }

    fun shouldBlock(url: String): Boolean {
        if (!ready) return false
        val host = extractHost(url) ?: return false
        return matchesHost(host)
    }

    private fun matchesHost(host: String): Boolean {
        if (blockedDomains.contains(host)) return true
        // check parent domains: ads.example.com → example.com
        var dot = host.indexOf('.')
        while (dot != -1) {
            val parent = host.substring(dot + 1)
            if (blockedDomains.contains(parent)) return true
            dot = parent.indexOf('.')
            if (dot != -1) dot += host.length - parent.length
        }
        return false
    }

    private fun extractHost(url: String): String? {
        return try {
            URI(url).host?.lowercase()?.removePrefix("www.")
        } catch (_: Exception) { null }
    }
}
