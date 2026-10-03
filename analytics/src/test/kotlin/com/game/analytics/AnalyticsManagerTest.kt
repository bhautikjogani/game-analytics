package com.game.analytics

import com.ogl.game.analytics.AnalyticsConfig
import org.junit.Assert.*
import org.junit.Test

class AnalyticsManagerTest {

    @Test fun trackAddsIdentityAndSessionContext() {
        val h = Harness()
        val gameId = h.manager.startGame("draw")
        h.manager.track("turn_played", mapOf("turn_number" to 5))
        val e = h.backend.events.last()
        assertEquals("turn_played", e.name)
        assertEquals(5L, e.params["turn_number"])
        assertEquals("IN", e.params["country"])
        assertEquals(gameId, e.params["game_id"])
        assertEquals("draw", e.params["game_mode"])
        assertNotNull(e.params["user_uuid"])
    }

    @Test fun gameCannotOverrideModuleManagedParameters() {
        val h = Harness()
        h.manager.track("x", mapOf("user_uuid" to "evil", "country" to "ZZ"))
        val e = h.backend.events.single()
        assertNotEquals("evil", e.params["user_uuid"])
        assertEquals("IN", e.params["country"])
    }

    @Test fun gameModeParameterPassesThroughWithoutActiveSession() {
        val h = Harness()
        h.manager.track("game_mode_selected", mapOf("game_mode" to "draw"))
        assertEquals("draw", h.backend.events.single().params["game_mode"])
    }

    @Test fun finishAddsDurationTurnsAndOnlyFiresOnce() {
        val h = Harness()
        h.manager.startGame("draw", mapOf("difficulty" to "medium"))
        repeat(3) { h.manager.incrementTurn() }
        h.clock.elapsed = 90_000
        assertTrue(h.manager.finishGame("win", mapOf("player_score" to 100)))
        assertFalse(h.manager.finishGame("win"))
        assertFalse(h.manager.abandonGame("navigation_away"))
        val finals = h.backend.events.filter { it.name == "game_finished" }
        assertEquals(1, finals.size)
        val p = finals.single().params
        assertEquals(90L, p["game_duration"]); assertEquals(3L, p["turn_count"])
        assertEquals("win", p["result"]); assertEquals(100L, p["player_score"]); assertEquals("medium", p["difficulty"])
        assertEquals(0, h.backend.events.count { it.name == "game_abandoned" })
    }

    @Test fun abandonRecordsOnlyTheGivenReason() {
        val h = Harness()
        h.manager.startGame()
        assertTrue(h.manager.abandonGame("navigation_away"))
        assertEquals("navigation_away", h.backend.events.last().params["abandon_reason"])
    }

    @Test fun userUuidIsCreatedOnceAndReused() {
        val storage = FakeStorage()
        val first = Harness(storage = storage).manager
        first.track("a")
        val saved = storage.uuid
        assertNotNull(saved)
        val second = Harness(storage = storage).manager
        second.track("b")
        assertEquals(saved, storage.uuid)
        assertEquals(saved, second.getUserUuid())
    }

    @Test fun countryFallsBackWithoutCrashing() {
        val h = Harness(country = null)
        h.manager.track("a")
        assertEquals("unknown", h.backend.events.single().params["country"])
        assertNull(h.storage.country) // fallback is not persisted
    }

    @Test fun disabledAnalyticsSendsNothingAndGameContinues() {
        val h = Harness(AnalyticsConfig(analyticsEnabled = false))
        val id = h.manager.startGame("draw")
        h.manager.track("a"); h.manager.setUserProperty("p", "v")
        assertTrue(h.backend.events.isEmpty()); assertTrue(h.backend.userProperties.isEmpty())
        assertEquals(id, h.manager.getCurrentGameId())
        assertNull(h.storage.uuid) // nothing created while disabled
        h.manager.enableAnalytics()
        h.manager.track("b")
        assertEquals(listOf("b"), h.backend.events.map { it.name })
        assertEquals(true, h.backend.collectionEnabled)
    }

    @Test fun invalidEventIsSanitizedNotCrashing() {
        val h = Harness()
        h.manager.track("bad name!", mapOf("ok" to 1))
        assertEquals("bad_name_", h.backend.events.single().name)
    }
}
