package com.ogl.game.analytics.core

/** Parameter names owned by the module. Games must not send these themselves. */
object AnalyticsKeys {
    const val USER_UUID = "user_uuid"
    const val COUNTRY = "country"
    const val GAME_ID = "game_id"
    const val GAME_MODE = "game_mode"
    const val GAME_DURATION = "game_duration" // seconds
    const val TURN_COUNT = "turn_count"
    const val RESULT = "result"
    const val ABANDON_REASON = "abandon_reason"
    const val APP_VERSION = "app_version"
    const val APP_BUILD = "app_build"
    const val PLATFORM = "platform"
    const val OS_VERSION = "os_version"

    /** Never accepted from game code, in any event. */
    val IDENTITY_KEYS: Set<String> = setOf(USER_UUID, COUNTRY)
}
