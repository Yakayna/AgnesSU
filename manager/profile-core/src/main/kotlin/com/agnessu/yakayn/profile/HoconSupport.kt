package com.agnessu.yakayn.profile

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValueFactory

object HoconSupport {

    fun parseValue(text: String): Any? {
        if (text.isBlank()) return null
        val config = ConfigFactory.parseString(text)
        return unwrap(config.root().unwrapped())
    }

    fun render(value: Any?): String {
        val config = ConfigValueFactory.fromAnyRef(value)
        return config.render(
            ConfigRenderOptions.defaults()
                .setOriginComments(false)
                .setComments(false)
                .setJson(false)
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun unwrap(value: Any?): Any? = when (value) {
        is Map<*, *> -> {
            val map = ValueMap()
            for ((k, v) in value) map[k.toString()] = unwrap(v)
            map
        }
        is List<*> -> {
            val list = ValueList()
            for (item in value) list.add(unwrap(item))
            list
        }
        else -> value
    }
}
