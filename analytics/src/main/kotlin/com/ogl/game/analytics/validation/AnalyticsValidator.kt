package com.ogl.game.analytics.validation

import com.ogl.game.analytics.AnalyticsConfig
import com.ogl.game.analytics.core.AnalyticsKeys
import kotlin.collections.iterator

/**
 * Validates and sanitizes data against Firebase Analytics limits. It never throws.
 *
 * Behavior (documented contract):
 *  - validationEnabled = false : only type coercion is applied (needed to build a Bundle).
 *  - lenient (default)         : invalid names are sanitized when safe (else the event/param is
 *                                dropped); bad values are coerced, truncated or dropped; excess
 *                                parameters are dropped. Issues are returned for logging.
 *  - strictValidation = true   : ANY issue drops the whole event / user property.
 *  - null parameter values are skipped silently (they are explicitly allowed).
 */
class AnalyticsValidator(private val config: AnalyticsConfig) {

    class Result<T>(val value: T?, val issues: List<String>)

    fun validateEventName(name: String): Result<String> {
        if (!config.validationEnabled) return Result(name, emptyList())
        val issues = ArrayList<String>()
        var out: String? = name
        if (!isValidName(name, MAX_EVENT_NAME)) {
            issues += "invalid event name '$name'"
            out = if (config.strictValidation) null else sanitizeName(name, MAX_EVENT_NAME)
        }
        if (out != null && (hasReservedPrefix(out) || out in RESERVED_EVENT_NAMES)) {
            issues += "reserved event name '$out'"
            out = null
        }
        return finish(out, issues)
    }

    fun validateParameters(raw: Map<String, Any?>, maxCount: Int = MAX_PARAMS_PER_EVENT): Result<Map<String, Any>> {
        val out = LinkedHashMap<String, Any>()
        val issues = ArrayList<String>()
        for ((rawKey, rawValue) in raw) {
            if (rawValue == null) continue
            var key: String? = rawKey
            if (config.validationEnabled) {
                if (!isValidName(rawKey, MAX_PARAM_NAME)) {
                    issues += "invalid parameter name '$rawKey'"
                    key = if (config.strictValidation) null else sanitizeName(rawKey, MAX_PARAM_NAME)
                }
                if (key != null && hasReservedPrefix(key)) {
                    issues += "reserved parameter prefix in '$key'"
                    key = null
                }
            }
            if (key == null) continue
            val coerced = coerce(rawValue)
            if (coerced == null) {
                issues += "unsupported or non-finite value for '$rawKey' (${rawValue::class.java.simpleName})"
                continue
            }
            var value: Any = coerced
            if (config.validationEnabled && value is String && value.length > MAX_PARAM_VALUE) {
                issues += "value of '$key' truncated to $MAX_PARAM_VALUE chars"
                value = value.take(MAX_PARAM_VALUE)
            }
            if (out.containsKey(key)) { issues += "duplicate parameter '$key' after sanitizing"; continue }
            if (config.validationEnabled && out.size >= maxCount) { issues += "too many parameters; '$key' dropped"; continue }
            out[key] = value
        }
        return if (config.strictValidation && config.validationEnabled && issues.isNotEmpty()) Result(null, issues)
        else Result(out, issues)
    }

    fun validateUserProperty(name: String, value: String?): Result<Pair<String, String?>> {
        if (!config.validationEnabled) return Result(name to value, emptyList())
        val issues = ArrayList<String>()
        var n: String? = name
        var v = value
        if (!isValidName(name, MAX_USER_PROPERTY_NAME)) {
            issues += "invalid user property name '$name'"
            n = if (config.strictValidation) null else sanitizeName(name, MAX_USER_PROPERTY_NAME)
        }
        if (n != null && (hasReservedPrefix(n) || n in RESERVED_USER_PROPERTIES)) {
            issues += "reserved user property '$n'"
            n = null
        }
        if (v != null && v.length > MAX_USER_PROPERTY_VALUE) {
            issues += "user property value truncated to $MAX_USER_PROPERTY_VALUE chars"
            v = v.take(MAX_USER_PROPERTY_VALUE)
        }
        return if (n == null || (config.strictValidation && issues.isNotEmpty())) Result(null, issues)
        else Result(n to v, issues)
    }

    /** Required global context that is missing from a finished parameter set. */
    fun missingContext(params: Map<String, Any>): List<String> =
        AnalyticsKeys.IDENTITY_KEYS.filter { it !in params }

    private fun <T> finish(value: T?, issues: List<String>): Result<T> =
        if (config.strictValidation && issues.isNotEmpty()) Result(null, issues) else Result(value, issues)

    private fun coerce(value: Any): Any? = when (value) {
        is String -> value
        is Boolean -> if (value) 1L else 0L // Firebase has no boolean type
        is Int, is Long, is Short, is Byte -> (value as Number).toLong()
        is Float, is Double -> (value as Number).toDouble().takeIf { it.isFinite() }
        is Enum<*> -> value.name.lowercase()
        is CharSequence -> value.toString()
        else -> null
    }

    private fun isValidName(name: String, max: Int) = name.length <= max && NAME_REGEX.matches(name)

    private fun sanitizeName(raw: String, max: Int): String? {
        val s = raw.trim().replace(INVALID_CHARS, "_").take(max)
        return s.takeIf { NAME_REGEX.matches(it) }
    }

    private fun hasReservedPrefix(name: String) = RESERVED_PREFIXES.any { name.startsWith(it, ignoreCase = true) }

    companion object {
        const val MAX_PARAMS_PER_EVENT = 25
        const val MAX_EVENT_NAME = 40
        const val MAX_PARAM_NAME = 40
        const val MAX_PARAM_VALUE = 100
        const val MAX_USER_PROPERTY_NAME = 24
        const val MAX_USER_PROPERTY_VALUE = 36

        private val NAME_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*$")
        private val INVALID_CHARS = Regex("[^A-Za-z0-9_]")
        private val RESERVED_PREFIXES = listOf("firebase_", "google_", "ga_")

        private val RESERVED_EVENT_NAMES = setOf(
            "ad_activeview", "ad_click", "ad_exposure", "ad_query", "ad_reward", "adunit_exposure",
            "app_background", "app_clear_data", "app_exception", "app_remove", "app_store_refund",
            "app_store_subscription_cancel", "app_store_subscription_convert", "app_store_subscription_renew",
            "app_update", "app_upgrade", "dynamic_link_app_open", "dynamic_link_app_update",
            "dynamic_link_first_open", "error", "first_open", "first_visit", "in_app_purchase",
            "notification_dismiss", "notification_foreground", "notification_open", "notification_receive",
            "os_update", "session_start", "session_start_with_rollout", "user_engagement",
        )
        private val RESERVED_USER_PROPERTIES = setOf(
            "first_open_time", "last_deep_link_referrer", "user_id", "first_open_after_install",
        )
    }
}
