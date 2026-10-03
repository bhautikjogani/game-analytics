package com.ogl.game.analytics.core

enum class EventCategory { GENERIC, GAME_LIFECYCLE, GAME_PROGRESS, MONETIZATION, APP_LIFECYCLE }

/** Generic event. Contains no game knowledge. */
data class AnalyticsEvent(
    val name: String,
    val parameters: Map<String, Any?> = emptyMap(),
    val category: EventCategory = EventCategory.GENERIC,
    val timestampMillis: Long = System.currentTimeMillis(),
)

/**
 * Implement on a game-owned enum/object to avoid string literals in game code:
 *
 *     enum class DominoesEvent(override val eventName: String) : EventDefinition {
 *         TURN_PLAYED("turn_played"), DRAW_TILE("draw_tile")
 *     }
 */
interface EventDefinition { val eventName: String }

/** Same idea for parameter keys (e.g. a game-owned enum). */
interface ParameterDefinition { val key: String }

class ParametersBuilder {
    private val map = LinkedHashMap<String, Any?>()
    fun put(key: String, value: Any?) { map[key] = value }
    fun put(key: ParameterDefinition, value: Any?) { map[key.key] = value }
    internal fun build(): Map<String, Any?> = map
}

/** analyticsParams { put(Param.SCORE, 100); put("result", "win") } */
fun analyticsParams(block: ParametersBuilder.() -> Unit): Map<String, Any?> =
    ParametersBuilder().apply(block).build()
