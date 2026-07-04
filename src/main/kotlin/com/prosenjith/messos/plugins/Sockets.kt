package com.prosenjith.messos.plugins

import io.ktor.server.application.*
import io.ktor.server.websocket.*

fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriodMillis = 15_000L
        timeoutMillis = 15_000L
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
}
