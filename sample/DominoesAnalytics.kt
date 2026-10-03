// INTEGRATION EXAMPLE ONLY. This file lives in the consuming game, never in :analytics.
package com.example.dominoes.analytics

import com.ogl.game.analytics.AnalyticsManager
import com.ogl.game.analytics.core.EventDefinition
import com.ogl.game.analytics.core.ParameterDefinition
import com.ogl.game.analytics.core.analyticsParams

enum class DominoesEvent(override val eventName: String) : EventDefinition {
    MODE_SELECTED("game_mode_selected"),
    TILE_PLAYED("tile_played"),
    TILE_DRAWN("tile_drawn"),
}

enum class DominoesParam(override val key: String) : ParameterDefinition {
    ENTRY_POINT("entry_point"),
    TILE_VALUE("tile_value"),
    PLAYER_SCORE("player_score"),
    OPPONENT_SCORE("opponent_score"),
    DIFFICULTY("difficulty"),
    PLAYER_COUNT("player_count"),
}

class DominoesAnalytics(private val analytics: AnalyticsManager) {

    fun modeSelected(mode: String, entryPoint: String) = analytics.track(
        DominoesEvent.MODE_SELECTED,
        analyticsParams { put("game_mode", mode); put(DominoesParam.ENTRY_POINT, entryPoint) },
    )

    fun gameStarted(mode: String, difficulty: String, players: Int) = analytics.startGame(
        gameMode = mode,
        metadata = analyticsParams {
            put(DominoesParam.DIFFICULTY, difficulty)
            put(DominoesParam.PLAYER_COUNT, players)
        },
    )

    fun tilePlayed(tileValue: Int) {
        analytics.incrementTurn() // the GAME decides what a "turn" is
        analytics.track(DominoesEvent.TILE_PLAYED, analyticsParams { put(DominoesParam.TILE_VALUE, tileValue) })
    }

    // "win"/"loss"/"draw" are Dominoes semantics, so they are defined here, not in the module.
    fun gameFinished(result: String, playerScore: Int, opponentScore: Int) = analytics.finishGame(
        result = result,
        parameters = analyticsParams {
            put(DominoesParam.PLAYER_SCORE, playerScore)
            put(DominoesParam.OPPONENT_SCORE, opponentScore)
        },
    )

    fun gameAbandoned(reason: String?) = analytics.abandonGame(reason)
}
