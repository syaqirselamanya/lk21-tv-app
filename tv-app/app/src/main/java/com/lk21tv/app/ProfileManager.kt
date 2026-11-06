package com.lk21tv.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class UserProfile(
    val id: String,
    val name: String,
    val colorIndex: Int
)

/**
 * Netflix-style local profiles (no login, no account, no cloud).
 * Every profile owns its own Continue Watching history and recommendation weights.
 * Data is stored on-device in SharedPreferences using keys scoped as "<baseKey>__<profileId>".
 */
object ProfileManager {
    private const val PREF_NAME = "citeraloka_prefs"
    private const val KEY_PROFILES = "profiles_json"
    private const val KEY_ACTIVE = "active_profile_id"

    const val MAX_PROFILES = 5

    val AVATAR_COLORS = listOf(
        "#00D4B2", // Electric Teal
        "#F59E0B", // Amber
        "#EF4444", // Red
        "#8B5CF6", // Violet
        "#3B82F6", // Blue
        "#EC4899"  // Pink
    )

    // Per-profile data keys (must match ContinueWatchingManager & UserRecommendationManager)
    private const val KEY_HISTORY = "continue_watching_list"
    private const val KEY_GENRE_WEIGHTS = "pref_genre_weights"
    private const val KEY_COUNTRY_WEIGHTS = "pref_country_weights"
    private const val KEY_WATCHED_URLS = "pref_watched_urls"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getProfiles(context: Context): List<UserProfile> {
        val result = mutableListOf<UserProfile>()
        try {
            val arr = JSONArray(prefs(context).getString(KEY_PROFILES, "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                result.add(
                    UserProfile(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        colorIndex = o.optInt("color", 0)
                    )
                )
            }
        } catch (_: Exception) {
        }
        return result.filter { it.id.isNotBlank() && it.name.isNotBlank() }
    }

    private fun saveProfiles(context: Context, list: List<UserProfile>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("color", p.colorIndex)
                }
            )
        }
        prefs(context).edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    fun getActiveId(context: Context): String? {
        val id = prefs(context).getString(KEY_ACTIVE, null) ?: return null
        return if (getProfiles(context).any { it.id == id }) id else null
    }

    fun getActive(context: Context): UserProfile? {
        val id = getActiveId(context) ?: return null
        return getProfiles(context).firstOrNull { it.id == id }
    }

    fun setActive(context: Context, id: String) {
        prefs(context).edit().putString(KEY_ACTIVE, id).apply()
    }

    /** Returns the preference key scoped to the active profile. */
    fun scopedKey(context: Context, baseKey: String): String {
        val id = getActiveId(context) ?: return baseKey
        return "${baseKey}__$id"
    }

    fun addProfile(context: Context, name: String, colorIndex: Int): UserProfile {
        val list = getProfiles(context).toMutableList()
        val profile = UserProfile(
            id = UUID.randomUUID().toString().take(8),
            name = name.trim(),
            colorIndex = colorIndex.coerceIn(0, AVATAR_COLORS.lastIndex)
        )
        val isFirstProfile = list.isEmpty()
        list.add(profile)
        saveProfiles(context, list)
        if (isFirstProfile) migrateLegacyData(context, profile.id)
        return profile
    }

    fun updateProfile(context: Context, id: String, name: String, colorIndex: Int) {
        val list = getProfiles(context).map {
            if (it.id == id) {
                it.copy(name = name.trim(), colorIndex = colorIndex.coerceIn(0, AVATAR_COLORS.lastIndex))
            } else {
                it
            }
        }
        saveProfiles(context, list)
    }

    /** Deletes a profile and all of its history. The last remaining profile cannot be deleted. */
    fun deleteProfile(context: Context, id: String): Boolean {
        val list = getProfiles(context).toMutableList()
        if (list.size <= 1) return false
        if (!list.removeAll { it.id == id }) return false
        saveProfiles(context, list)

        val editor = prefs(context).edit()
        listOf(KEY_HISTORY, KEY_GENRE_WEIGHTS, KEY_COUNTRY_WEIGHTS, KEY_WATCHED_URLS).forEach { key ->
            editor.remove("${key}__$id")
        }
        if (prefs(context).getString(KEY_ACTIVE, null) == id) {
            editor.putString(KEY_ACTIVE, list.first().id)
        }
        editor.apply()
        return true
    }

    fun colorOf(profile: UserProfile): String =
        AVATAR_COLORS[profile.colorIndex.coerceIn(0, AVATAR_COLORS.lastIndex)]

    fun initialOf(name: String): String =
        name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    /**
     * Existing users already have history & recommendation data saved before profiles existed.
     * The very first profile they create inherits that data so nothing is lost on update.
     */
    private fun migrateLegacyData(context: Context, profileId: String) {
        val p = prefs(context)
        val editor = p.edit()
        listOf(KEY_HISTORY, KEY_GENRE_WEIGHTS, KEY_COUNTRY_WEIGHTS).forEach { key ->
            val value = try {
                p.getString(key, null)
            } catch (_: Exception) {
                null
            }
            if (value != null) editor.putString("${key}__$profileId", value)
        }
        val watched = try {
            p.getStringSet(KEY_WATCHED_URLS, null)
        } catch (_: Exception) {
            null
        }
        if (watched != null) editor.putStringSet("${KEY_WATCHED_URLS}__$profileId", watched.toSet())
        editor.apply()
    }
}
