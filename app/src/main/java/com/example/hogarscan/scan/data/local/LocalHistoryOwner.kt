package com.example.hogarscan.scan.data.local

import android.content.Context
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalHistoryOwner(context: Context) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()

    suspend fun getOwnerId(): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val preferences = appContext.getSharedPreferences(
                "history_profile",
                Context.MODE_PRIVATE
            )
            preferences.getString("owner_id", null)
                ?.takeIf { it.isNotBlank() }
                ?: ("local:" + UUID.randomUUID()).also { id ->
                    check(preferences.edit().putString("owner_id", id).commit()) {
                        "No se pudo guardar el perfil local."
                    }
                }
        }
    }
}