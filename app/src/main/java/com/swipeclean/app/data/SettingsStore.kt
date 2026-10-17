package com.swipeclean.app.data

import android.content.Context
import kotlin.random.Random

/** Filtros, orden aleatorio estable y estado de la sesión. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("swipeclean", Context.MODE_PRIVATE)

    fun loadFilter(): Filter = Filter(
        albumId = prefs.getString(KEY_ALBUM, null),
        year = prefs.getInt(KEY_YEAR, -1).takeIf { it > 0 },
        month = prefs.getInt(KEY_MONTH, -1).takeIf { it > 0 },
        order = runCatching { SortOrder.valueOf(prefs.getString(KEY_ORDER, null) ?: "") }
            .getOrDefault(SortOrder.NEWEST),
    )

    fun saveFilter(f: Filter) {
        prefs.edit()
            .putString(KEY_ALBUM, f.albumId)
            .putInt(KEY_YEAR, f.year ?: -1)
            .putInt(KEY_MONTH, f.month ?: -1)
            .putString(KEY_ORDER, f.order.name)
            .apply()
    }

    var sessionActive: Boolean
        get() = prefs.getBoolean(KEY_SESSION, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SESSION, value).apply()
        }

    /** Semilla fija para que el orden aleatorio se mantenga al cerrar y reabrir la app. */
    val randomSeed: Long
        get() {
            if (!prefs.contains(KEY_SEED)) newSeed()
            return prefs.getLong(KEY_SEED, 1L)
        }

    fun newSeed() {
        prefs.edit().putLong(KEY_SEED, Random.nextLong()).apply()
    }

    private companion object {
        const val KEY_ALBUM = "album"
        const val KEY_YEAR = "year"
        const val KEY_MONTH = "month"
        const val KEY_ORDER = "order"
        const val KEY_SESSION = "session_active"
        const val KEY_SEED = "random_seed"
    }
}
