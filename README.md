# Core Analytics Module

A lightweight, powerful, and highly generic analytics module designed to be used across any App or Game. It serves as a unified abstraction over Firebase Analytics (or any backend) and automatically handles global user context and session timing.

## Features
- **Auto-Timestamps**: Every event automatically gets a precise `timestamp` injected.
- **Auto-UUID**: Automatically generates and injects a unique `user_uuid` for every event and user property.
- **Auto-Country**: Automatically detects the user's country (via Telephony Manager or Locale) and injects `country`.
- **Session & Durations**: Built-in timer logic automatically calculates the `duration_seconds` for game rounds, screens, or features.
- **Clean and Generic**: Contains zero game-specific logic. It doesn't know about "Dominoes" or "Matches"—it just handles smart event tracking perfectly.
- **Safe Values**: Automatically sanitizes string lengths and drops blank strings so Firebase doesn't throw errors.

## Implementation Guide

### 1. Initialization (Application Class)
Initialize the module once when your app starts (usually in your `Application` class).

```kotlin
import com.ogl.game.analytics.CoreAnalyticsManager

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize the generic analytics manager
        CoreAnalyticsManager.initialize(this, firebaseEnabled = true)
    }
}
```

### 2. Basic Event Tracking
Use `.track()` to log simple, one-off events. The `user_uuid`, `timestamp`, and `country` are automatically injected for you!

```kotlin
val analytics = CoreAnalyticsManager.getInstance()

// Simple event with parameters
analytics.track("ad_clicked", mapOf(
    "placement" to "store_double_coins",
    "ad_type" to "rewarded"
))

// Dialog tracking
analytics.track("dialog_opened", mapOf(
    "dialog_id" to "daily_reward"
))
```

### 3. Screen Tracking
Helper method to easily track screens.

```kotlin
analytics.trackScreenView("StoreScreen", "Activity")
```

### 4. Tracking Time and Durations (Game Rounds, Matches, Features)
Instead of manually calculating how long a user spent on a level or in a game, use the Timer Events.

**A. Start a Timer**
```kotlin
// A user starts a game match
val matchId = "match_12345" // Some unique ID for this instance

analytics.startTimerEvent(
    timerId = matchId,
    eventName = "game_match",
    params = mapOf(
        "mode" to "JB_Draw",
        "coins" to 500,
        "points" to 200
    )
)
// Resulting Firebase Event: 
// name: "game_match", action: "start", mode: "JB_Draw", timestamp: 123456789, country: "US", user_uuid: "..."
```

**B. Stop a Timer**
```kotlin
// The user finishes the match. 
// Use the same timerId to automatically calculate how long they played.
analytics.stopTimerEvent(
    timerId = matchId,
    eventName = "game_match",
    params = mapOf(
        "result" to "win",
        "player_score" to 150
    )
)
// Resulting Firebase Event: 
// name: "game_match", action: "stop", result: "win", duration_seconds: 142.5, timestamp: ..., country: "US", user_uuid: "..."
```

### 5. Setting User Properties
If you need to define traits for the user (like their tier or total lifetime spend).
```kotlin
analytics.setUserProperty("player_tier", "gold")
```

## Parameter Guide

When tracking custom maps, ensure your data types are valid:
- `String`: Must not be blank. Max 100 characters.
- `Number`: Int, Long, Float, or Double.
- `Boolean`: Converted automatically to `1L` (true) or `0L` (false) since Firebase does not natively support booleans.

Keys must be 40 characters or less.

## Setting Up Firebase Dashboard

For your custom parameters to show up in the Firebase Console:
1. Go to Firebase Console -> Analytics -> Custom Definitions.
2. For strings (like `mode`, `result`), create a **Custom Dimension**.
3. For numbers (like `points`, `duration_seconds`), create a **Custom Metric**.
4. The **Event Parameter** field must exactly match the key you use in your Kotlin map.
