package com.ogl.game.analytics

/** Optional app metadata that can be attached to every event. Off by default (Firebase already collects these). */
enum class CommonParameter { APP_VERSION, APP_BUILD, PLATFORM, OS_VERSION }

/** Names of the generic session lifecycle events. Override per game if you want different names. */
data class SessionEventNames(
    val gameStarted: String = "game_started",
    val gameFinished: String = "game_finished",
    val gameAbandoned: String = "game_abandoned",
)

/**
 * Names of optional app lifecycle events.
 * NOTE: "app_background" is a reserved Firebase event name, so the defaults differ.
 */
data class LifecycleEventNames(
    val foreground: String = "app_entered_foreground",
    val background: String = "app_entered_background",
)

data class AnalyticsConfig(
    /**
     * Initial collection state. Set to false until the host app has obtained any consent it needs,
     * then call AnalyticsManager.setAnalyticsEnabled(true). The module does not persist this flag;
     * the host app owns its consent state and must re-apply it on each launch.
     */
    val analyticsEnabled: Boolean = true,
    /** null = auto (enabled only when the app is debuggable). */
    val debugLoggingEnabled: Boolean? = null,
    /** Validate/sanitize names, types, lengths and counts against Firebase limits. */
    val validationEnabled: Boolean = true,
    /** true = any validation issue drops the whole event/property instead of sanitizing. */
    val strictValidation: Boolean = false,
    /** Master switch for attaching module-managed context (user_uuid, country, common params). */
    val automaticContextEnabled: Boolean = true,
    /** Attach game_id / game_mode to every event tracked while a game session is active. */
    val automaticGameSessionEnabled: Boolean = true,
    /** false = never talk to Firebase (events are still validated and logged). */
    val firebaseEnabled: Boolean = true,
    /** Extra metadata attached to every event, in addition to user_uuid and country. */
    val commonParameters: Set<CommonParameter> = emptySet(),
    /** Also call FirebaseAnalytics.setUserId(user_uuid). The UUID is anonymous. */
    val setFirebaseUserId: Boolean = false,
    /** Emit foreground/background events via ProcessLifecycleOwner. Never creates abandonment events. */
    val trackAppLifecycle: Boolean = false,
    val sessionEventNames: SessionEventNames = SessionEventNames(),
    val lifecycleEventNames: LifecycleEventNames = LifecycleEventNames(),
)
