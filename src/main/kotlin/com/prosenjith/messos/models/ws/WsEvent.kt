package com.prosenjith.messos.models.ws

import kotlinx.serialization.Serializable

@Serializable
data class WsEvent(
    val type: String,
    val data: Map<String, String> = emptyMap()
)
