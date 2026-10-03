package com.ogl.game.analytics.core

import com.ogl.game.analytics.AnalyticsConfig
import com.ogl.game.analytics.country.CountryProvider
import com.ogl.game.analytics.firebase.AnalyticsBackend
import com.ogl.game.analytics.logging.AnalyticsLogger
import com.ogl.game.analytics.session.GameSession
import com.ogl.game.analytics.storage.AnalyticsStorage
import com.ogl.game.analytics.validation.AnalyticsValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.collections.iterator

/**
 * Event pipeline: validate -> enrich (identity, app info, session) -> log -> backend.
 *
 * Callers only enqueue (non-blocking, any thread). A single consumer coroutine processes events in
 * order on the injected scope's dispatcher, so DataStore access and identity loading never touch
 * the main thread, and events keep their call order. Session state is captured at call time.
 */
internal class AnalyticsEngine(
    private val config: AnalyticsConfig,
    private val storage: AnalyticsStorage,
    private val countryProvider: CountryProvider,
    private val backend: AnalyticsBackend,
    private val validator: AnalyticsValidator,
    private val logger: AnalyticsLogger,
    private val appInfo: AppInfo,
    private val scope: CoroutineScope,
) {
    private class Pending(val event: AnalyticsEvent, val session: GameSession?, val system: Map<String, Any>)

    private val queue = Channel<Pending>(capacity = MAX_PENDING, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val identityMutex = Mutex()
    private val fallbackUuid by lazy { UUID.randomUUID().toString() }
    private val _context = MutableStateFlow<AnalyticsContext?>(null)

    val context: StateFlow<AnalyticsContext?> = _context.asStateFlow()

    @Volatile
    var isEnabled: Boolean = config.analyticsEnabled
        private set

    init {
        val initial = isEnabled
        scope.launch { attempt("apply collection state") { backend.setCollectionEnabled(initial) } }
        scope.launch {
            for (pending in queue) {
                try {
                    process(pending)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    logger.error("event processing failed", t)
                }
            }
        }
        // While disabled nothing is read or created; identity is loaded on first need.
        if (initial && config.automaticContextEnabled) scope.launch { attempt("load identity") { ensureContext() } }
    }

    // ---------------------------------------------------------------- public (internal) API

    fun enqueue(event: AnalyticsEvent, session: GameSession?, system: Map<String, Any> = emptyMap()) {
        if (!isEnabled) {
            logger.debug("analytics disabled; dropped '${event.name}'")
            return
        }
        queue.trySend(Pending(event, session, system)) // DROP_OLDEST: never blocks or fails
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        if (!enabled) while (queue.tryReceive().isSuccess) { /* discard anything still pending */ }
        scope.launch {
            attempt("set collection enabled") { backend.setCollectionEnabled(enabled) }
            if (enabled && config.automaticContextEnabled && _context.value == null) {
                attempt("load identity") { ensureContext() }
            }
        }
    }

    fun setUserProperty(name: String, value: String?) {
        if (!isEnabled) { logger.debug("analytics disabled; dropped user property '$name'"); return }
        scope.launch {
            attempt("set user property") {
                val r = validator.validateUserProperty(name, value)
                r.issues.forEach { logger.warn("user property '$name': $it") }
                val v = r.value
                if (v == null) logger.warn("user property '$name' dropped")
                else {
                    logger.debug("user property ${v.first} = ${v.second}")
                    backend.setUserProperty(v.first, v.second)
                }
            }
        }
    }

    /** Loads (or creates) the identity even while analytics is disabled; nothing is sent. */
    suspend fun awaitContext(): AnalyticsContext = ensureContext()

    suspend fun resetIdentity() {
        identityMutex.withLock {
            attempt("clear identity") { storage.clearIdentity() }
            attempt("clear firebase user id") { backend.setUserId(null) }
            _context.value = null
        }
    }

    // ---------------------------------------------------------------- processing

    private suspend fun process(p: Pending) {
        if (!isEnabled) return
        val ctx = if (config.automaticContextEnabled) ensureContext() else null

        val nameResult = validator.validateEventName(p.event.name)
        nameResult.issues.forEach { logger.warn("event '${p.event.name}': $it") }
        val name = nameResult.value ?: run {
            logger.warn("event dropped: '${p.event.name}'")
            return
        }

        // Module-managed parameters always win over anything the game supplied.
        val forced = LinkedHashMap<String, Any>()
        if (ctx != null) {
            forced[AnalyticsKeys.USER_UUID] = ctx.userUuid
            forced[AnalyticsKeys.COUNTRY] = ctx.country
            forced.putAll(appInfo.toParameters(config.commonParameters))
        }
        p.session?.let { s ->
            if (config.automaticGameSessionEnabled) {
                forced[AnalyticsKeys.GAME_ID] = s.gameId
                s.gameMode?.let { forced[AnalyticsKeys.GAME_MODE] = it }
            }
        }
        forced.putAll(p.system)

        val userParams = LinkedHashMap<String, Any?>()
        for ((k, v) in p.event.parameters) {
            when {
                k in AnalyticsKeys.IDENTITY_KEYS -> logger.warn("'$k' is module-managed; removed from '$name'")
                k in forced -> logger.warn("'$k' is module-managed here; game value ignored in '$name'")
                else -> userParams[k] = v
            }
        }

        val budget = (AnalyticsValidator.MAX_PARAMS_PER_EVENT - forced.size).coerceAtLeast(0)
        val paramResult = validator.validateParameters(userParams, budget)
        paramResult.issues.forEach { logger.warn("event '$name': $it") }
        val validated = paramResult.value ?: run {
            logger.warn("event dropped (strict validation): '$name'")
            return
        }

        val finalParams = LinkedHashMap<String, Any>(forced)
        finalParams.putAll(validated)

        if (ctx != null) {
            val missing = validator.missingContext(finalParams)
            if (missing.isNotEmpty()) logger.warn("event '$name' is missing context: $missing")
        }

        if (!isEnabled) return // disabled while this event was being prepared
        logger.event(name, finalParams)
        attempt("log event") { backend.logEvent(name, finalParams) }
    }

    // ---------------------------------------------------------------- identity

    private suspend fun ensureContext(): AnalyticsContext {
        _context.value?.let { return it }
        return identityMutex.withLock { _context.value ?: loadContext().also { _context.value = it } }
    }

    private suspend fun loadContext(): AnalyticsContext {
        val uuid = attempt("load user uuid") {
            storage.getUserUuid()?.takeIf { it.isNotBlank() }
                ?: UUID.randomUUID().toString().also { storage.saveUserUuid(it) }
        } ?: fallbackUuid // storage broken: stable for this process, not persisted

        val stored = attempt("load country") { storage.getCountry()?.takeIf { it.isNotBlank() } }
        val country = stored
            ?: attempt("resolve country") { countryProvider.resolveCountry() }?.also { resolved ->
                attempt("save country") { storage.saveCountry(resolved) }
            }
            ?: AnalyticsContext.UNKNOWN_COUNTRY // not persisted, so it is retried on the next launch

        if (config.setFirebaseUserId) attempt("set firebase user id") { backend.setUserId(uuid) }
        return AnalyticsContext(uuid, country)
    }

    private inline fun <T> attempt(what: String, block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        logger.error("$what failed", t)
        null
    }

    private companion object { const val MAX_PENDING = 1_000 }
}
