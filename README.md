# Common Analytics Module (`:analytics`)

Game-agnostic Android analytics infrastructure on top of Firebase Analytics.
No Dominoes/Ludo/Rummy knowledge lives here; games define their own events and meanings.

## Setup (consuming a game)
1. Add the GitHub Packages repo to the game's `settings.gradle.kts` (see `sample/consumer-settings.gradle.kts`).
2. In the game's **app** module: apply the `google-services` plugin, add `google-services.json`, add `implementation("<group>:analytics:<version>")`.
3. Call `AnalyticsManager.initialize(this)` once in `Application.onCreate()` (idempotent).

## Publishing (this repo -> private GitHub Packages)
One-time:
1. Create a **private** GitHub repo and push this project. Run `gradle wrapper` once to generate `gradlew` and `gradle/wrapper/`, then commit them.
2. Edit `gradle.properties`: set `analytics.group` (pick once, never change) and `analytics.githubRepo`.
3. Rename the package/namespace `com.ogl.game.analytics` now, before the first release.
4. Check the `agp` and `kotlin` versions in `gradle/libs.versions.toml`; they are unverified placeholders.

Release (CI, recommended): `git tag v1.0.0 && git push origin v1.0.0`. The workflow runs the tests and publishes using the built-in `GITHUB_TOKEN`.

Release (local): put a classic personal access token with `write:packages` in `~/.gradle/gradle.properties` as `gpr.user` / `gpr.token`, then run
`./gradlew :analytics:publishReleasePublicationToGitHubPackagesRepository`.

Reading the package from a game (local or CI):
- Local: a classic token with `read:packages` in `~/.gradle/gradle.properties`.
- Game repo CI: either give that repo access under the package's settings (Package settings -> Manage Actions access) so `GITHUB_TOKEN` works, or store a token as a secret and expose it as `GITHUB_TOKEN`/`gpr.token`.
- Never commit tokens. Released versions are immutable; bump the version for every release.

Versions used: BoM 34.19.0, Google Services 4.5.0, DataStore 1.2.1 (your reference values). The Firebase release-notes page I could reach listed up to BoM 34.17.0 (July 30, 2026), so re-check the newest BoM yourself. Coroutines and lifecycle versions are my best guesses; check them too.

## Public API
| Call | Purpose |
|---|---|
| `track(event, params)` | Generic event; adds `user_uuid`, `country`, and `game_id`/`game_mode` while a session is active |
| `startGame(gameMode, metadata)` | Returns generated `game_id`; emits `game_started` |
| `incrementTurn()` | Counts game-defined turns |
| `finishGame(result, params, gameId?)` | Emits `game_finished` once, with `game_duration` (seconds) and `turn_count` |
| `abandonGame(reason, params, gameId?)` | Explicit only; emits `game_abandoned` once |
| `resetGameSession()` | Drops the session silently |
| `setUserProperty(name, value)` | Validated, forwarded to Firebase |
| `setAnalyticsEnabled(bool)` / `setAnalyticsConsent(bool)` | Technical consent switch |
| `getUserUuid()` / `awaitUserUuid()` / `getCountry()` / `getCurrentGameId()` | Read module state |
| `resetIdentity()` | Clears stored UUID and country |

## Behavior you should know
- **Threading**: all calls are non-blocking. One background consumer processes events in order; DataStore and country detection never run on the main thread. Session state is captured at call time.
- **Identity**: UUID is created once and stored in Preferences DataStore. Country is SIM, then network, then locale region (no permission needed). It is persisted once resolved. If it cannot be resolved, `"unknown"` is sent and retried next launch.
- **Disabled**: no events or user properties are sent, Firebase collection is turned off, and nothing is read or created in storage until enabled. Game sessions keep working. User properties set while disabled are not replayed; re-apply them after enabling.
- **Consent**: the flag is not persisted by the module. Initialize with `analyticsEnabled = false`, then call `setAnalyticsConsent(true)` from your own consent state on each launch.
- **Validation** (`AnalyticsValidator`): lenient by default. Invalid names are sanitized when safe (else dropped), reserved Firebase names/prefixes are dropped, bad types are dropped, strings are cut to 100 chars, extras beyond 25 params are dropped. `strictValidation = true` drops the whole event on any issue. Nothing ever throws. Booleans are sent as `1`/`0`.
- **Module-owned keys**: `user_uuid` and `country` supplied by a game are always removed. `game_id`, `game_mode`, `game_duration`, `turn_count` win over game values only when the module is attaching them.
- **Duplicate protection**: a closed session cannot emit a second final event. Pass `gameId` to finish/abandon to guard against late calls hitting the next game.
- **Starting a game while one is active** replaces it and logs a warning. No abandonment is invented.
- **Lifecycle**: `trackAppLifecycle` is off by default. Event names are `app_entered_foreground`/`app_entered_background` because `app_background` is reserved by Firebase. It never touches game sessions.
- **Firebase console**: `user_uuid`, `country`, `game_id`, etc. must be registered as custom dimensions to appear in reports.

## Tests
`./gradlew :analytics:testDebugUnitTest` (validator, session manager, and full pipeline with fakes).
