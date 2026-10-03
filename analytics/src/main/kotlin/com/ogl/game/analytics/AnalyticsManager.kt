package com.ogl.game.analytics

import android.content.Context
import android.content.pm.ApplicationInfo
import com.ogl.game.analytics.core.AnalyticsContext
import com.ogl.game.analytics.core.AnalyticsEngine
import com.ogl.game.analytics.core.AnalyticsEvent
import com.ogl.game.analytics.core.AnalyticsKeys
import com.ogl.game.analytics.core.AppInfo
import com.ogl.game.analytics.core.EventCategory
import com.ogl.game.analytics.core.EventDefinition
import com.ogl.game.analytics.core.SystemAnalyticsClock
import com.ogl.game.analytics.country.DeviceCountryProvider
import com.ogl.game.analytics.firebase.AnalyticsBackend
import com.ogl.game.analytics.firebase.FirebaseAnalyticsAdapter
import com.ogl.game.analytics.firebase.NoOpAnalyticsBackend
import com.ogl.game.analytics.lifecycle.AppLifecycleTracker
import com.ogl.game.analytics.logging.AnalyticsLogger
import com.ogl.game.analytics.session.CloseResult
import com.ogl.game.analytics.session.GameSession
import com.ogl.game.analytics.session.GameSessionManager
import com.ogl.game.analytics.session.GameSessionState
import com.ogl.game.analytics.storage.DataStoreAnalyticsStorage
import com.ogl.game.analytics.validation.AnalyticsValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Public entry point. Game-agnostic: games define their own event names, parameters and meaning.
 * Every method is non-blocking, callable from the main thread, and never throws.
 */
class AnalyticsManager internal constructor(
    private val config: AnalyticsConfig,
    private val engine: AnalyticsEngine,
    private val sessions: GameSessionManager,
    private val logger: AnalyticsLogger,
) {

    // ------------------------------------------------------------------ generic events

    fun track(
        event: String,
        parameters: Map<String, Any?> = emptyMap(),
        category: EventCategory = EventCategory.GENERIC,
    ) = guard {
        // Copy now: the caller may mutate its map before the event is processed.
        engine.enqueue(AnalyticsEvent(event, LinkedHashMap(parameters), category), sessions.current())
    }

    fun track(event: EventDefinition, parameters: Map<String, Any?> = emptyMap()) =
        track(event.eventName, parameters)

    // ------------------------------------------------------------------ game session

    /**
     * Starts a game and returns its generated game_id. If a session is still active it is replaced
     * (logged) WITHOUT inventing an abandonment event; call abandonGame() first if you know it was
     * abandoned.
     */
    fun startGame(gameMode: String? = null, metadata: Map<String, Any?> = emptyMap()): String =
        guard(UUID.randomUUID().toString()) {
            val result = sessions.start(gameMode, metadata)
            result.replaced?.let { logger.warn("startGame replaced active session ${it.gameId} without a final event") }
            val s = result.session
            val system = LinkedHashMap<String, Any>()
            system[AnalyticsKeys.GAME_ID] = s.gameId
            s.gameMode?.let { system[AnalyticsKeys.GAME_MODE] = it }
            engine.enqueue(
                AnalyticsEvent(
                    config.sessionEventNames.gameStarted,
                    LinkedHashMap(metadata),
                    EventCategory.GAME_LIFECYCLE
                ),
                s,
                system,
            )
            s.gameId
        }

    /** Counts one game-defined "turn". The module does not interpret what a turn is. Returns the new count. */
    fun incrementTurn(by: Int = 1): Int = guard(0) { sessions.incrementTurn(by) }

    /**
     * Closes the active game as finished and emits the final event once. Returns false when ignored
     * (no active game, already closed, or [gameId] does not match).
     */
    fun finishGame(
        result: String? = null,
        parameters: Map<String, Any?> = emptyMap(),
        gameId: String? = null,
    ): Boolean = closeGame(
        GameSessionState.FINISHED, config.sessionEventNames.gameFinished,
        AnalyticsKeys.RESULT, result, parameters, gameId,
    )

    /** Explicit abandonment only. Nothing in the module infers it from lifecycle callbacks. */
    fun abandonGame(
        reason: String? = null,
        parameters: Map<String, Any?> = emptyMap(),
        gameId: String? = null,
    ): Boolean = closeGame(
        GameSessionState.ABANDONED, config.sessionEventNames.gameAbandoned,
        AnalyticsKeys.ABANDON_REASON, reason, parameters, gameId,
    )

    /** Silently discards the active session (no event). */
    fun resetGameSession() = guard { sessions.reset()?.let { logger.debug("game session ${it.gameId} reset") } }

    fun getCurrentGameId(): String? = guard(null) { sessions.currentGameId() }

    fun getCurrentGameSession(): GameSession? = guard(null) { sessions.current() }

    private fun closeGame(
        state: GameSessionState,
        eventName: String,
        valueKey: String,
        value: String?,
        parameters: Map<String, Any?>,
        gameId: String?,
    ): Boolean = guard(false) {
        when (val r = sessions.close(state, gameId)) {
            is CloseResult.Ignored -> {
                logger.warn("$state ignored: ${r.reason}")
                false
            }
            is CloseResult.Closed -> {
                val s = r.session
                val params = LinkedHashMap<String, Any?>(s.metadata) // start metadata, overridable
                params.putAll(parameters)
                if (value != null) params[valueKey] = value
                val system = LinkedHashMap<String, Any>()
                system[AnalyticsKeys.GAME_ID] = s.gameId
                s.gameMode?.let { system[AnalyticsKeys.GAME_MODE] = it }
                system[AnalyticsKeys.GAME_DURATION] = s.durationSeconds ?: 0L
                system[AnalyticsKeys.TURN_COUNT] = s.turnCount.toLong()
                engine.enqueue(AnalyticsEvent(eventName, params, EventCategory.GAME_LIFECYCLE), s, system)
                true
            }
        }
    }

    // ------------------------------------------------------------------ user properties / identity

    fun setUserProperty(name: String, value: String?) = guard { engine.setUserProperty(name, value) }

    /** Anonymous id, or null until identity has been loaded (see [awaitUserUuid]). */
    fun getUserUuid(): String? = engine.context.value?.userUuid

    fun getCountry(): String? = engine.context.value?.country

    val analyticsContext: StateFlow<AnalyticsContext?> get() = engine.context

    /** Suspends until identity is loaded/created. Works even while analytics is disabled; sends nothing. */
    suspend fun awaitUserUuid(): String = engine.awaitContext().userUuid

    /** Wipes the stored user_uuid and country; a new identity is created on next use. */
    suspend fun resetIdentity() = engine.resetIdentity()

    // ------------------------------------------------------------------ enable / consent

    val isAnalyticsEnabled: Boolean get() = engine.isEnabled

    fun setAnalyticsEnabled(enabled: Boolean) = guard { engine.setEnabled(enabled) }

    fun enableAnalytics() = setAnalyticsEnabled(true)

    fun disableAnalytics() = setAnalyticsEnabled(false)

    /** Technical switch only; the host app owns the consent UI and policy. */
    fun setAnalyticsConsent(granted: Boolean) = setAnalyticsEnabled(granted)

    // ------------------------------------------------------------------ internals

    private inline fun <T> guard(default: T, block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        logger.error("analytics call failed", t)
        default
    }

    private inline fun guard(block: () -> Unit) = guard(Unit, block)

    companion object {
        @Volatile private var instance: AnalyticsManager? = null

        /** Idempotent. Later calls return the existing instance and ignore [config]. */
        @JvmStatic
        fun initialize(context: Context, config: AnalyticsConfig = AnalyticsConfig()): AnalyticsManager =
            instance ?: synchronized(this) {
                instance ?: create(context.applicationContext, config).also { instance = it }
            }

        fun getInstanceOrNull(): AnalyticsManager? = instance

        @OptIn(ExperimentalCoroutinesApi::class)
        private fun create(appContext: Context, config: AnalyticsConfig): AnalyticsManager {
            val debuggable = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val logger = AnalyticsLogger(config.debugLoggingEnabled ?: debuggable)

            val backend: AnalyticsBackend =
                if (config.firebaseEnabled) FirebaseAnalyticsAdapter(appContext) else NoOpAnalyticsBackend

            // One thread: ordered processing and no concurrent DataStore/identity races.
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
            val engine = AnalyticsEngine(
                config = config,
                storage = DataStoreAnalyticsStorage(appContext),
                countryProvider = DeviceCountryProvider(appContext),
                backend = backend,
                validator = AnalyticsValidator(config),
                logger = logger,
                appInfo = AppInfo.from(appContext),
                scope = scope,
            )
            val manager = AnalyticsManager(config, engine,
                GameSessionManager(SystemAnalyticsClock), logger)

            if (config.trackAppLifecycle) {
                AppLifecycleTracker(
                    onForeground = {
                        manager.track(
                            config.lifecycleEventNames.foreground,
                            category = EventCategory.APP_LIFECYCLE
                        )
                    },
                    onBackground = {
                        manager.track(
                            config.lifecycleEventNames.background,
                            category = EventCategory.APP_LIFECYCLE
                        )
                    },
                ).register()
            }
            return manager
        }
    }
}
