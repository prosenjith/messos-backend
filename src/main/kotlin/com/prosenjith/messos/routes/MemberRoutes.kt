package com.prosenjith.messos.routes

import com.prosenjith.messos.models.ApiSuccess
import com.prosenjith.messos.models.member.ChangeRoleRequest
import com.prosenjith.messos.models.member.ChangeRoleResponse
import com.prosenjith.messos.models.member.RemoveMemberResponse
import com.prosenjith.messos.models.ws.WsEvent
import com.prosenjith.messos.services.MemberService
import com.prosenjith.messos.util.ForbiddenException
import com.prosenjith.messos.util.WebSocketManager
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.memberRoutes(memberService: MemberService) {
    authenticate("jwt-auth") {
        route("/members/{memberId}") {
            delete {
                val principal = call.principal<JWTPrincipal>()!!
                val callerUserId = UUID.fromString(principal.payload.subject)
                val messId = principal.payload.getClaim("messId").asString()
                    ?.let { UUID.fromString(it) }
                    ?: throw ForbiddenException("You must join a mess first")
                val targetUserId = UUID.fromString(call.parameters["memberId"]!!)

                memberService.removeMember(callerUserId, messId, targetUserId)
                call.respond(HttpStatusCode.OK, ApiSuccess(data = RemoveMemberResponse(removed = true)))
                WebSocketManager.broadcastToMess(messId, WsEvent("MEMBER_REMOVED", mapOf(
                    "memberId" to targetUserId.toString()
                )))
            }

            put("/role") {
                val principal = call.principal<JWTPrincipal>()!!
                val callerUserId = UUID.fromString(principal.payload.subject)
                val messId = principal.payload.getClaim("messId").asString()
                    ?.let { UUID.fromString(it) }
                    ?: throw ForbiddenException("You must join a mess first")
                val targetUserId = UUID.fromString(call.parameters["memberId"]!!)
                val body = call.receive<ChangeRoleRequest>()

                val newRole = memberService.changeMemberRole(callerUserId, messId, targetUserId, body.role)
                call.respond(HttpStatusCode.OK, ApiSuccess(data = ChangeRoleResponse(
                    memberId = targetUserId.toString(),
                    newRole  = newRole.name
                )))
                WebSocketManager.broadcastToMess(messId, WsEvent("MEMBER_ROLE_CHANGED", mapOf(
                    "memberId" to targetUserId.toString()
                )))
            }
        }
    }
}
