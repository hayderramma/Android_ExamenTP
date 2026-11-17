package com.example.contacts.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.preference.PreferenceManager

/** Lecture centralisée et conversion des préférences en valeurs prêtes à l'UI. */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    data class UiPreferences(
        val textSizeSp: Float,
        val textColor: Int,
        val showCallButton: Boolean
    )

    fun read(): UiPreferences {
        val taille = prefs.getString(KEY_TEXT_SIZE, DEFAULT_TEXT_SIZE)
            ?.toFloatOrNull()
            ?: DEFAULT_TEXT_SIZE.toFloat()

        val hex = prefs.getString(KEY_COLOR, DEFAULT_COLOR) ?: DEFAULT_COLOR
        val couleur = try {
            Color.parseColor(hex)
        } catch (_: IllegalArgumentException) {
            Color.BLACK
        }

        val appel = prefs.getBoolean(KEY_CALL_FROM_LIST, false)

        return UiPreferences(taille, couleur, appel)
    }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        const val KEY_TEXT_SIZE = "taille_police"
        const val KEY_COLOR = "couleur_preferee"
        const val KEY_CALL_FROM_LIST = "appel_depuis_liste"
        const val DEFAULT_TEXT_SIZE = "16"
        const val DEFAULT_COLOR = "#000000"
    }
}
