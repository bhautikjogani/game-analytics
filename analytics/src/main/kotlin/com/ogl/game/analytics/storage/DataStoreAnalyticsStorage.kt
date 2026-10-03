package com.ogl.game.analytics.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.io.IOException

// One DataStore instance per process for this file name (DataStore requirement).
private val Context.commonAnalyticsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "common_analytics",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class DataStoreAnalyticsStorage(
    private val dataStore: DataStore<Preferences>,
) : AnalyticsStorage {

    constructor(context: Context) : this(context.applicationContext.commonAnalyticsDataStore)

    private object Keys {
        val USER_UUID = stringPreferencesKey("user_uuid")
        val COUNTRY = stringPreferencesKey("country")
    }

    private suspend fun read(): Preferences =
        try { dataStore.data.first() } catch (e: IOException) { emptyPreferences() }

    override suspend fun getUserUuid(): String? = read()[Keys.USER_UUID]

    override suspend fun saveUserUuid(uuid: String) {
        dataStore.edit { it[Keys.USER_UUID] = uuid }
    }

    override suspend fun getCountry(): String? = read()[Keys.COUNTRY]

    override suspend fun saveCountry(country: String) {
        dataStore.edit { it[Keys.COUNTRY] = country }
    }

    override suspend fun clearIdentity() {
        dataStore.edit {
            it.remove(Keys.USER_UUID)
            it.remove(Keys.COUNTRY)
        }
    }
}
