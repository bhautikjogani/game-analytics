package com.game.analytics

import com.ogl.game.analytics.AnalyticsConfig
import com.ogl.game.analytics.core.AnalyticsClock
import com.ogl.game.analytics.core.AnalyticsEngine
import com.ogl.game.analytics.core.AppInfo
import com.ogl.game.analytics.country.CountryProvider
import com.ogl.game.analytics.firebase.AnalyticsBackend
import com.ogl.game.analytics.AnalyticsManager
import com.ogl.game.analytics.logging.AnalyticsLogger
import com.ogl.game.analytics.session.GameSessionManager
import com.ogl.game.analytics.storage.AnalyticsStorage
import com.ogl.game.analytics.validation.AnalyticsValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class FakeClock(var elapsed: Long = 0L, var wall: Long = 1_000L) : AnalyticsClock {
    override fun elapsedRealtimeMillis() = elapsed
    override fun currentTimeMillis() = wall
}

class FakeStorage(var uuid: String? = null, var country: String? = null) : AnalyticsStorage {
    override suspend fun getUserUuid() = uuid
    override suspend fun saveUserUuid(uuid: String) { this.uuid = uuid }
    override suspend fun getCountry() = country
    override suspend fun saveCountry(country: String) { this.country = country }
    override suspend fun clearIdentity() { uuid = null; country = null }
}

class FakeBackend : AnalyticsBackend {
    class Logged(val name: String, val params: Map<String, Any>)
    val events = ArrayList<Logged>()
    val userProperties = ArrayList<Pair<String, String?>>()
    var collectionEnabled: Boolean? = null
    override fun logEvent(name: String, params: Map<String, Any>) { events += Logged(name, params) }
    override fun setUserProperty(name: String, value: String?) { userProperties += name to value }
    override fun setUserId(id: String?) = Unit
    override fun setCollectionEnabled(enabled: Boolean) { collectionEnabled = enabled }
}

class Harness(
    config: AnalyticsConfig = AnalyticsConfig(),
    val storage: FakeStorage = FakeStorage(),
    val backend: FakeBackend = FakeBackend(),
    val clock: FakeClock = FakeClock(),
    country: String? = "IN",
) {
    // Unconfined => the engine's consumer runs inline, so assertions can follow calls directly.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val logger = AnalyticsLogger(enabled = false)
    private val engine = AnalyticsEngine(
        config, storage, CountryProvider { country }, backend,
        AnalyticsValidator(config), logger, AppInfo("1.0", 1L, "14"), scope,
    )
    val manager = AnalyticsManager(config, engine, GameSessionManager(clock), logger)
}
