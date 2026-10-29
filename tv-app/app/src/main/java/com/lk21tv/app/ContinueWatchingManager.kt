package com.lk21tv.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ContinueWatchingItem(
    val id: String,
    val title: String,
    val pageUrl: String,
    val embedUrl: String,
    val posterUrl: String,
    val currentSec: Int,
    val durationSec: Int,
    val lastWatchedTimestamp: Long,
    val episodeLabel: String = "",
    val serverName: String = "P2P Direct",
    val genre: String = ""
) {
    val progressPercent: Int
        get() = if (durationSec > 0) ((currentSec.toLong() * 100L) / durationSec).toInt().coerceIn(0, 100) else 0

    val remainingMinutes: Int
        get() = if (durationSec > currentSec) ((durationSec - currentSec) / 60) else 0
}

object ContinueWatchingManager {
    private const val PREF_NAME = "citeraloka_prefs"
    private const val KEY_HISTORY = "continue_watching_list"
    private const val MAX_ITEMS = 20

    fun saveProgress(
        context: Context,
        pageUrl: String,
        embedUrl: String,
        title: String,
        posterUrl: String,
        currentSec: Int,
        durationSec: Int,
        episodeLabel: String = "",
        serverName: String = "P2P Direct",
        genre: String = ""
    ) {
        if (pageUrl.isBlank() || currentSec < 5) return

        val list = getList(context).toMutableList()

        // Buang rekod lama jika tajuk atau URL sama
        list.removeAll { it.pageUrl == pageUrl || it.title.equals(title, ignoreCase = true) }

        // Jika sudah tonton lebih 95%, anggap sudah selesai dan tidak perlu lagi dalam 'Sambung Menonton'
        if (durationSec > 0 && (currentSec.toFloat() / durationSec.toFloat()) >= 0.95f) {
            saveList(context, list)
            return
        }

        val newItem = ContinueWatchingItem(
            id = pageUrl,
            title = title,
            pageUrl = pageUrl,
            embedUrl = embedUrl,
            posterUrl = posterUrl,
            currentSec = currentSec,
            durationSec = durationSec,
            lastWatchedTimestamp = System.currentTimeMillis(),
            episodeLabel = episodeLabel,
            serverName = serverName,
            genre = genre
        )

        list.add(0, newItem)
        if (list.size > MAX_ITEMS) {
            list.removeAt(list.size - 1)
        }

        saveList(context, list)
    }

    fun getList(context: Context): List<ContinueWatchingItem> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val result = mutableListOf<ContinueWatchingItem>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                result.add(
                    ContinueWatchingItem(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        pageUrl = obj.optString("pageUrl"),
                        embedUrl = obj.optString("embedUrl"),
                        posterUrl = obj.optString("posterUrl"),
                        currentSec = obj.optInt("currentSec"),
                        durationSec = obj.optInt("durationSec"),
                        lastWatchedTimestamp = obj.optLong("lastWatchedTimestamp"),
                        episodeLabel = obj.optString("episodeLabel"),
                        serverName = obj.optString("serverName", "P2P Direct"),
                        genre = obj.optString("genre")
                    )
                )
            }
        } catch (_: Exception) {}
        return result
    }

    fun remove(context: Context, pageUrl: String) {
        val list = getList(context).filterNot { it.pageUrl == pageUrl }
        saveList(context, list)
    }

    private fun saveList(context: Context, list: List<ContinueWatchingItem>) {
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("pageUrl", item.pageUrl)
                put("embedUrl", item.embedUrl)
                put("posterUrl", item.posterUrl)
                put("currentSec", item.currentSec)
                put("durationSec", item.durationSec)
                put("lastWatchedTimestamp", item.lastWatchedTimestamp)
                put("episodeLabel", item.episodeLabel)
                put("serverName", item.serverName)
                put("genre", item.genre)
            }
            arr.put(obj)
        }
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }
}
