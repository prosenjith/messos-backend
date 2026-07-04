package com.prosenjith.messos.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.prosenjith.messos.config.JwtConfig
import com.prosenjith.messos.util.WebSocketManager
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import java.util.UUID

fun Route.webSocketRoute(jwtConfig: JwtConfig) {
    webSocket("/ws") {
        val token = call.request.queryParameters["token"]
        if (token == null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing token"))
            return@webSocket
        }

        val payload = try {
            JWT.require(Algorithm.HMAC256(jwtConfig.secret))
                .withIssuer(jwtConfig.issuer)
                .withAudience(jwtConfig.audience)
                .build()
                .verify(token)
        } catch (e: Exception) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid or expired token"))
            return@webSocket
        }

        val messIdStr = payload.getClaim("messId").asString()
        if (messIdStr == null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No messId claim — join a mess first"))
            return@webSocket
        }

        val messId = try {
            UUID.fromString(messIdStr)
        } catch (e: Exception) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid messId"))
            return@webSocket
        }

        WebSocketManager.connect(messId, this)
        try {
            for (frame in incoming) {
                if (frame is Frame.Close) break
            }
        } catch (_: Exception) {
            // connection dropped
        } finally {
            WebSocketManager.disconnect(messId, this)
        }
    }
}
