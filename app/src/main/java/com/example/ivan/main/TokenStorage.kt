package com.example.ivan.main

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "app_prefs")

object TokenStorage {
    private val KEY_TOKEN = stringPreferencesKey("yandex_token")

    suspend fun saveToken(context: Context, token: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TOKEN] = token
        }
    }

    suspend fun getToken(context: Context): String? {
        return context.dataStore.data
            .map { prefs -> prefs[KEY_TOKEN] }
            .first()
    }

    suspend fun clearToken(context: Context) {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_TOKEN)
        }
    }
}