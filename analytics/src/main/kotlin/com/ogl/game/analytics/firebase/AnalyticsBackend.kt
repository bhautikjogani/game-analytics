package com.ogl.game.analytics.firebase

/** Transport abstraction. Parameter values are already validated: only String, Long or Double. */
interface AnalyticsBackend {
    fun logEvent(name: String, params: Map<String, Any>)
    fun setUserProperty(name: String, value: String?)
    fun setUserId(id: String?)
    fun setCollectionEnabled(enabled: Boolean)
}

object NoOpAnalyticsBackend : AnalyticsBackend {
    override fun logEvent(name: String, params: Map<String, Any>) = Unit
    override fun setUserProperty(name: String, value: String?) = Unit
    override fun setUserId(id: String?) = Unit
    override fun setCollectionEnabled(enabled: Boolean) = Unit
}
