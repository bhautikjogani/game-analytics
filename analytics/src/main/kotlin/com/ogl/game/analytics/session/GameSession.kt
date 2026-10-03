package com.ogl.game.analytics.session

enum class GameSessionState { ACTIVE, FINISHED, ABANDONED }

/** Immutable snapshot of a game session. */
data class GameSession(
    val gameId: String,
    val gameMode: String?,
    val metadata: Map<String, Any?>,
    val startedAtMillis: Long,
    val turnCount: Int,
    val state: GameSessionState,
    /** Whole seconds; only set once the session is closed. */
    val durationSeconds: Long? = null,
)
