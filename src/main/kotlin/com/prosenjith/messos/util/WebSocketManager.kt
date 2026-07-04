package com.prosenjith.messos.util

import com.prosenjith.messos.models.ws.WsEvent
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

object WebSocketManager {
    private val sessions = ConcurrentHashMap<UUID, CopyOnWriteArraySet<DefaultWebSocketServerSession>>()

    fun connect(messId: UUID, session: DefaultWebSocketServerSession) {
        sessions.getOrPut(messId) { CopyOnWriteArraySet() }.add(session)
    }

    fun disconnect(messId: UUID, session: DefaultWebSocketServerSession) {
        sessions[messId]?.remove(session)
    }

    suspend fun broadcastToMess(messId: UUID, event: WsEvent) {
        val json = Json.encodeToString(event)
        sessions[messId]?.forEach { session ->
            runCatching { session.send(Frame.Text(json)) }
        }
    }
}
