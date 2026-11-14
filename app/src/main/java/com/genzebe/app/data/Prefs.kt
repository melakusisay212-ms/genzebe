package com.genzebe.app.data

import android.content.Context

/** Small settings kept on the phone. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("genzebe", Context.MODE_PRIVATE)

    var onboardingDone: Boolean
        get() = sp.getBoolean("onboardingDone", false)
        set(value) { sp.edit().putBoolean("onboardingDone", value).apply() }

    /** "MONTH" = read this Ethiopian month's SMS, "TODAY" = only new SMS from now on. */
    var startMode: String
        get() = sp.getString("startMode", START_TODAY) ?: START_TODAY
        set(value) { sp.edit().putString("startMode", value).apply() }

    /** SMS older than this are never read. */
    var startFromMillis: Long
        get() = sp.getLong("startFromMillis", 0L)
        set(value) { sp.edit().putLong("startFromMillis", value).apply() }

    var ethiopianClock: Boolean
        get() = sp.getBoolean("ethiopianClock", true)
        set(value) { sp.edit().putBoolean("ethiopianClock", value).apply() }

    var hideBalance: Boolean
        get() = sp.getBoolean("hideBalance", false)
        set(value) { sp.edit().putBoolean("hideBalance", value).apply() }

    companion object {
        const val START_MONTH = "MONTH"
        const val START_TODAY = "TODAY"
    }
}
