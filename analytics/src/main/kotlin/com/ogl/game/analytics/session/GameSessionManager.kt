package com.ogl.game.analytics.session

import com.ogl.game.analytics.core.AnalyticsClock
import java.util.UUID

class StartResult(val session: GameSession, val replaced: GameSession?)

sealed interface CloseResult {
    class Closed(val session: GameSession) : CloseResult
    class Ignored(val reason: String) : CloseResult
}

/**
 * Thread-safe, game-agnostic session state. Pure logic: no Firebase, no logging, no Android
 * dependencies besides the injected clock, so it is trivially unit-testable.
 */
class GameSessionManager(
    private val clock: AnalyticsClock,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    private class Record(
        val gameId: String,
        val gameMode: String?,
        val metadata: Map<String, Any?>,
        val startedAtMillis: Long,
        val startedElapsed: Long,
        var turnCount: Int = 0,
    )

    private val lock = Any()
    private var active: Record? = null
    private val closedIds = LinkedHashSet<String>() // bounded memory of recently closed games

    fun start(gameMode: String?, metadata: Map<String, Any?>): StartResult = synchronized(lock) {
        val replaced = active?.let { snapshot(it, GameSessionState.ACTIVE, null) }
        val record = Record(
            gameId = idGenerator(),
            gameMode = gameMode,
            metadata = LinkedHashMap(metadata),
            startedAtMillis = clock.currentTimeMillis(),
            startedElapsed = clock.elapsedRealtimeMillis(),
        )
        active = record
        StartResult(snapshot(record, GameSessionState.ACTIVE, null), replaced)
    }

    /** Returns the new turn count, or 0 when no session is active. */
    fun incrementTurn(by: Int = 1): Int = synchronized(lock) {
        val r = active ?: return 0
        r.turnCount += by
        r.turnCount
    }

    fun current(): GameSession? = synchronized(lock) { active?.let { snapshot(it, GameSessionState.ACTIVE, null) } }

    fun currentGameId(): String? = synchronized(lock) { active?.gameId }

    /**
     * Closes the active session as FINISHED or ABANDONED. A closed session can never produce a
     * second final event: repeated or mismatched calls come back as Ignored.
     */
    fun close(finalState: GameSessionState, expectedGameId: String? = null): CloseResult = synchronized(lock) {
        require(finalState != GameSessionState.ACTIVE) { "finalState must be FINISHED or ABANDONED" }
        if (expectedGameId != null && expectedGameId in closedIds) {
            return CloseResult.Ignored("game $expectedGameId is already closed")
        }
        val r = active ?: return CloseResult.Ignored("no active game session")
        if (expectedGameId != null && expectedGameId != r.gameId) {
            return CloseResult.Ignored("game $expectedGameId is not the active game")
        }
        val seconds = ((clock.elapsedRealtimeMillis() - r.startedElapsed) / 1000L).coerceAtLeast(0L)
        active = null
        closedIds.add(r.gameId)
        if (closedIds.size > MAX_REMEMBERED_CLOSED) closedIds.remove(closedIds.first())
        CloseResult.Closed(snapshot(r, finalState, seconds))
    }

    /** Drops the active session silently (no event). */
    fun reset(): GameSession? = synchronized(lock) {
        val r = active
        active = null
        r?.let { snapshot(it, GameSessionState.ACTIVE, null) }
    }

    private fun snapshot(r: Record, state: GameSessionState, duration: Long?) = GameSession(
        gameId = r.gameId,
        gameMode = r.gameMode,
        metadata = r.metadata,
        startedAtMillis = r.startedAtMillis,
        turnCount = r.turnCount,
        state = state,
        durationSeconds = duration,
    )

    private companion object { const val MAX_REMEMBERED_CLOSED = 64 }
}
