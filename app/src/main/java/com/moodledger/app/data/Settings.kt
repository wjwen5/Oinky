package com.moodledger.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tiny SharedPreferences wrapper; the values are few and read synchronously at startup. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _mainCurrency = MutableStateFlow(prefs.getString(KEY_MAIN, "SGD")!!)
    val mainCurrency: StateFlow<String> = _mainCurrency.asStateFlow()

    private val _diaryReminder = MutableStateFlow(prefs.getBoolean(KEY_DIARY_REMINDER, true))
    val diaryReminder: StateFlow<Boolean> = _diaryReminder.asStateFlow()

    fun setMainCurrency(code: String) {
        prefs.edit().putString(KEY_MAIN, code).apply()
        _mainCurrency.value = code
    }

    fun setDiaryReminder(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DIARY_REMINDER, enabled).apply()
        _diaryReminder.value = enabled
    }

    /** Used to notify about missed payments at most once per day. */
    var lastMissedNotifiedDay: Long
        get() = prefs.getLong(KEY_MISSED_DAY, -1)
        set(v) = prefs.edit().putLong(KEY_MISSED_DAY, v).apply()

    var lastDiaryNudgeDay: Long
        get() = prefs.getLong(KEY_DIARY_DAY, -1)
        set(v) = prefs.edit().putLong(KEY_DIARY_DAY, v).apply()

    private companion object {
        const val KEY_MAIN = "main_currency"
        const val KEY_DIARY_REMINDER = "diary_reminder"
        const val KEY_MISSED_DAY = "missed_notified_day"
        const val KEY_DIARY_DAY = "diary_nudge_day"
    }
}
