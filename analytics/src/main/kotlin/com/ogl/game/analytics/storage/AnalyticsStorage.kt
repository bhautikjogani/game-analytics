package com.ogl.game.analytics.storage

/** Replaceable persistence for module-managed identity. No Firebase knowledge allowed here. */
interface AnalyticsStorage {
    suspend fun getUserUuid(): String?
    suspend fun saveUserUuid(uuid: String)
    suspend fun getCountry(): String?
    suspend fun saveCountry(country: String)
    /** Removes the stored user_uuid and country. */
    suspend fun clearIdentity()
}
