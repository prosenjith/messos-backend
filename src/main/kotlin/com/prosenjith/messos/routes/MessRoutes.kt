package com.prosenjith.messos.routes

import com.prosenjith.messos.config.JwtConfig
import com.prosenjith.messos.models.ApiSuccess
import com.prosenjith.messos.models.mess.CreateMessRequest
import com.prosenjith.messos.models.mess.JoinMessRequest
import com.prosenjith.messos.models.mess.LeaveMessResponse
import com.prosenjith.messos.models.mess.MemberInfo
import com.prosenjith.messos.models.mess.MessDetailResponse
import com.prosenjith.messos.models.mess.MessResponse
import com.prosenjith.messos.models.mess.MessWithTokenResponse
import com.prosenjith.messos.models.ws.WsEvent
import com.prosenjith.messos.services.MessService
import com.prosenjith.messos.util.ForbiddenException
import com.prosenjith.messos.util.ValidationException
import com.prosenjith.messos.util.WebSocketManager
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.messRoutes(messService: MessService, jwtConfig: JwtConfig) {
    authenticate("jwt-auth") {
        post("/mess") {
            val userId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            val req = call.receive<CreateMessRequest>()
            val result = messService.createMess(userId, req.name, req.cycleStartDay, jwtConfig)
            call.respond(
                HttpStatusCode.Created,
                ApiSuccess(data = MessWithTokenResponse(
                    mess = result.mess.toResponse(),
                    token = result.token
                ))
            )
        }

        post("/mess/join") {
            val userId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            val req = call.receive<JoinMessRequest>()
            val result = messService.joinMess(userId, req.joinCode, jwtConfig)
            call.respond(
                HttpStatusCode.OK,
                ApiSuccess(data = MessWithTokenResponse(
                    mess = result.mess.toResponse(),
                    token = result.token
                ))
            )
        }

        post("/mess/leave") {
            val principal = call.principal<JWTPrincipal>()!!
            val userId = UUID.fromString(principal.payload.subject)
            val messId = principal.payload.getClaim("messId").asString()
                ?.let { UUID.fromString(it) }
                ?: throw ForbiddenException("You must join a mess first")

            val memberId = messService.leaveMess(userId, messId)
            call.respond(HttpStatusCode.OK, ApiSuccess(data = LeaveMessResponse(left = true)))
            WebSocketManager.broadcastToMess(messId, WsEvent("MEMBER_LEFT", mapOf(
                "memberId" to memberId.toString()
            )))
        }

        get("/mess/{id}") {
            val userId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            val messId = try {
                UUID.fromString(call.parameters["id"])
            } catch (e: IllegalArgumentException) {
                throw ValidationException("Invalid mess id")
            }
            val detail = messService.getMessById(messId, userId)
            call.respond(
                ApiSuccess(data = MessDetailResponse(
                    id = detail.mess.id.toString(),
                    name = detail.mess.name,
                    joinCode = detail.mess.joinCode,
                    managerId = detail.mess.managerId.toString(),
                    cycleStartDay = detail.mess.cycleStartDay,
                    createdAt = detail.mess.createdAt,
                    members = detail.members.map { MemberInfo(it.id.toString(), it.name, it.role, it.status) }
                ))
            )
        }
    }
}

private fun com.prosenjith.messos.services.MessRecord.toResponse() = MessResponse(
    id = id.toString(),
    name = name,
    joinCode = joinCode,
    managerId = managerId.toString(),
    cycleStartDay = cycleStartDay,
    createdAt = createdAt
)
