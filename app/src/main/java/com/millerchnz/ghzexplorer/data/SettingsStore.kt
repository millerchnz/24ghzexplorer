package com.millerchnz.ghzexplorer.data

import android.content.Context

/** Remembers that the first-run tour has been finished. Scan results are not stored here. */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isFeatureTourCompleted(): Boolean = prefs.getBoolean(KEY_TOUR, false)

    fun setFeatureTourCompleted() {
        prefs.edit().putBoolean(KEY_TOUR, true).apply()
    }

    companion object {
        const val PREFS = "ghz_explorer"
        private const val KEY_TOUR = "feature_tour_completed"
    }
}
