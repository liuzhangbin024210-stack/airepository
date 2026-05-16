package com.majiang.counter.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_session")

data class AuthSession(val username: String)

@Singleton
class AuthSessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyUsername = stringPreferencesKey("logged_in_username")

    val session: Flow<AuthSession?> = context.authDataStore.data.map { prefs ->
        prefs[keyUsername]?.let { AuthSession(it) }
    }

    suspend fun save(username: String) {
        context.authDataStore.edit { it[keyUsername] = username }
    }

    suspend fun clear() {
        context.authDataStore.edit { it.remove(keyUsername) }
    }
}
