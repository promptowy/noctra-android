package com.promptowy.noctra

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class Profile(val name: String, val color: String, val group: String = "general")

class ProfileManager(context: Context) {
    private val file = File(context.filesDir, "profiles.json")
    private val gson = Gson()

    companion object {
        val COLORS = listOf("#34D24B", "#22D3EE", "#F472B6", "#FBBF24", "#A78BFA", "#FB7185")
        const val DEFAULT = "Default"
    }

    data class ProfilesData(val profiles: MutableList<Profile>, val active: String)

    private var data: ProfilesData = load()
    val profiles get() = data.profiles
    var activeProfile get() = data.active; set(v) { data = data.copy(active = v); save() }

    private fun load(): ProfilesData = try {
        gson.fromJson(file.readText(), ProfilesData::class.java)
            ?: ProfilesData(mutableListOf(Profile(DEFAULT, COLORS[0])), DEFAULT)
    } catch (_: Exception) {
        ProfilesData(mutableListOf(Profile(DEFAULT, COLORS[0])), DEFAULT)
    }

    private fun save() { file.writeText(gson.toJson(data)) }

    fun addProfile(name: String, group: String = "general"): Profile? {
        if (profiles.any { it.name == name }) return null
        val color = COLORS[profiles.size % COLORS.size]
        val p = Profile(name, color, group)
        profiles.add(p)
        save()
        return p
    }

    fun getColor(name: String): String = profiles.find { it.name == name }?.color ?: COLORS[0]
}
