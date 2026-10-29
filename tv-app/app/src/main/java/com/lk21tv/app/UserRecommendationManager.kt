package com.lk21tv.app

import android.content.Context
import org.json.JSONObject

object UserRecommendationManager {
    private const val PREF_NAME = "citeraloka_prefs"
    private const val KEY_GENRE_WEIGHTS = "pref_genre_weights"
    private const val KEY_COUNTRY_WEIGHTS = "pref_country_weights"
    private const val KEY_WATCHED_URLS = "pref_watched_urls"

    fun recordView(context: Context, genre: String, url: String, weightBoost: Int = 1) {
        if (genre.isBlank() && url.isBlank()) return
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        // Kemas kini kegemaran genre pengguna
        val genreJson = JSONObject(prefs.getString(KEY_GENRE_WEIGHTS, "{}") ?: "{}")
        genre.split(",", "/", "&").map { it.trim() }.filter { it.isNotBlank() }.forEach { g ->
            val cur = genreJson.optInt(g, 0)
            genreJson.put(g, cur + weightBoost)
        }

        // Kemas kini kegemaran negara / rantau (contoh: Korea, Barat, China)
        val countryJson = JSONObject(prefs.getString(KEY_COUNTRY_WEIGHTS, "{}") ?: "{}")
        val possibleCountries = listOf("korea", "china", "usa", "barat", "japan", "india", "thailand")
        for (c in possibleCountries) {
            if (url.lowercase().contains(c) || genre.lowercase().contains(c)) {
                val cur = countryJson.optInt(c, 0)
                countryJson.put(c, cur + weightBoost)
            }
        }

        // Simpan URL yang telah ditonton
        val watchedSet = prefs.getStringSet(KEY_WATCHED_URLS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        if (url.isNotBlank()) watchedSet.add(url)

        prefs.edit()
            .putString(KEY_GENRE_WEIGHTS, genreJson.toString())
            .putString(KEY_COUNTRY_WEIGHTS, countryJson.toString())
            .putStringSet(KEY_WATCHED_URLS, watchedSet)
            .apply()
    }

    /**
     * Algoritma Skor Cadangan Pintar:
     * Menilai setiap kandungan berdasarkan:
     * 1. Keutamaan Genre Pengguna (3.5x pengganda)
     * 2. Keutamaan Rantau/Negara Pengguna (2.5x pengganda)
     * 3. Penarafan IMDb (1.8x pengganda)
     * 4. Faktor Tahun Terkini (2026/2025 bonus)
     * 5. Penemuan Tajuk Baharu (belum ditonton diberi keutamaan)
     */
    fun getRecommendedItems(context: Context, allItems: List<MediaItem>): List<MediaItem> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val genreJson = JSONObject(prefs.getString(KEY_GENRE_WEIGHTS, "{}") ?: "{}")
        val countryJson = JSONObject(prefs.getString(KEY_COUNTRY_WEIGHTS, "{}") ?: "{}")
        val watchedSet = prefs.getStringSet(KEY_WATCHED_URLS, emptySet()) ?: emptySet()

        val scored = allItems.map { item ->
            var score = 0.0

            // 1. Padanan Genre
            val genres = item.genre.split(",", "/", "&").map { it.trim() }.filter { it.isNotBlank() }
            for (g in genres) {
                val weight = genreJson.optInt(g, 0)
                score += weight * 3.5
            }

            // 2. Padanan Negara
            val countryKeys = countryJson.keys()
            while (countryKeys.hasNext()) {
                val cKey = countryKeys.next()
                if (item.url.lowercase().contains(cKey) || item.genre.lowercase().contains(cKey)) {
                    val weight = countryJson.optInt(cKey, 0)
                    score += weight * 2.5
                }
            }

            // 3. Pengaruh Rating IMDb
            val ratingVal = item.rating.toDoubleOrNull() ?: 6.5
            score += ratingVal * 1.8

            // 4. Bonus Tajuk Terkini
            if (item.title.contains("2026")) score += 4.0
            else if (item.title.contains("2025")) score += 2.0

            // 5. Galakkan Penemuan Cerita Baharu
            if (!watchedSet.contains(item.url)) {
                score += 3.0
            } else {
                score -= 8.0
            }

            item to score
        }

        return scored.sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.url }
            .take(12)
    }
}
