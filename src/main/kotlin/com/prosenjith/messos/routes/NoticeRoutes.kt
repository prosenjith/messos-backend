package com.prosenjith.messos.routes

import com.prosenjith.messos.models.ApiSuccess
import com.prosenjith.messos.models.meal.DeletedResponse
import com.prosenjith.messos.models.notice.NoticeResponse
import com.prosenjith.messos.models.notice.PostNoticeRequest
import com.prosenjith.messos.services.NoticeRecord
import com.prosenjith.messos.services.NoticeService
import com.prosenjith.messos.util.ForbiddenException
import com.prosenjith.messos.util.ValidationException
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.noticeRoutes(noticeService: NoticeService) {
    authenticate("jwt-auth") {
        route("/notices") {
            post {
                val principal = call.principal<JWTPrincipal>()!!
                val callerUserId = UUID.fromString(principal.payload.subject)
                val messId = principal.payload.getClaim("messId").asString()
                    ?.let { UUID.fromString(it) }
                    ?: throw ForbiddenException("You must join a mess before posting notices")

                val req = call.receive<PostNoticeRequest>()
                val notice = noticeService.postNotice(callerUserId, messId, req.message)
                call.respond(HttpStatusCode.Created, ApiSuccess(data = notice.toResponse()))
            }

            get {
                val principal = call.principal<JWTPrincipal>()!!
                val messId = principal.payload.getClaim("messId").asString()
                    ?.let { UUID.fromString(it) }
                    ?: throw ForbiddenException("You must join a mess before viewing notices")

                val notices = noticeService.getNotices(messId)
                call.respond(ApiSuccess(data = notices.map { it.toResponse() }))
            }

            delete("/{id}") {
                val principal = call.principal<JWTPrincipal>()!!
                val callerUserId = UUID.fromString(principal.payload.subject)
                val messId = principal.payload.getClaim("messId").asString()
                    ?.let { UUID.fromString(it) }
                    ?: throw ForbiddenException("You must join a mess before deleting notices")

                val noticeId = try {
                    UUID.fromString(call.parameters["id"])
                } catch (e: IllegalArgumentException) {
                    throw ValidationException("Invalid notice id")
                }
                noticeService.deleteNotice(callerUserId, messId, noticeId)
                call.respond(ApiSuccess(data = DeletedResponse()))
            }
        }
    }
}

private fun NoticeRecord.toResponse() = NoticeResponse(
    id           = id.toString(),
    message      = message,
    postedBy     = postedByUserId.toString(),
    postedByName = postedByName,
    createdAt    = createdAt
)
