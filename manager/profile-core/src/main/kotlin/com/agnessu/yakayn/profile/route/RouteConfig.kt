package com.agnessu.yakayn.profile.route

sealed interface RouteConfig {
    fun entries(): List<Pair<String, ULong>>
    fun apply(key: String, value: ULong): RouteConfig
}

object NoRouteConfig : RouteConfig {
    override fun entries(): List<Pair<String, ULong>> = emptyList()
    override fun apply(key: String, value: ULong): RouteConfig = this
}
