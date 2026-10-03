package com.game.analytics

import com.ogl.game.analytics.session.CloseResult
import com.ogl.game.analytics.session.GameSessionManager
import com.ogl.game.analytics.session.GameSessionState
import org.junit.Assert.*
import org.junit.Test

class GameSessionManagerTest {
    private val clock = FakeClock()
    private var n = 0
    private val sessions = GameSessionManager(clock) { "game-${++n}" }

    @Test fun startCreatesUniqueIdsAndResetsTurns() {
        val a = sessions.start("draw", mapOf("difficulty" to "medium")).session
        sessions.incrementTurn(); sessions.incrementTurn()
        val b = sessions.start(null, emptyMap()).session
        assertNotEquals(a.gameId, b.gameId)
        assertEquals(0, sessions.current()!!.turnCount)
    }

    @Test fun finishComputesDurationAndTurns() {
        sessions.start("draw", emptyMap())
        repeat(24) { sessions.incrementTurn() }
        clock.elapsed = 182_900
        val closed = (sessions.close(GameSessionState.FINISHED) as CloseResult.Closed).session
        assertEquals(182L, closed.durationSeconds)
        assertEquals(24, closed.turnCount)
        assertNull(sessions.currentGameId())
    }

    @Test fun secondCloseIsIgnored() {
        val id = sessions.start(null, emptyMap()).session.gameId
        assertTrue(sessions.close(GameSessionState.FINISHED, id) is CloseResult.Closed)
        assertTrue(sessions.close(GameSessionState.ABANDONED, id) is CloseResult.Ignored)
        assertTrue(sessions.close(GameSessionState.FINISHED) is CloseResult.Ignored)
    }

    @Test fun mismatchedGameIdIsIgnored() {
        sessions.start(null, emptyMap())
        assertTrue(sessions.close(GameSessionState.FINISHED, "other") is CloseResult.Ignored)
        assertNotNull(sessions.currentGameId())
    }

    @Test fun turnsWithoutSessionAreIgnored() {
        assertEquals(0, sessions.incrementTurn())
    }
}
