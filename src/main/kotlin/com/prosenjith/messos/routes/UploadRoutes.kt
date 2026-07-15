package com.prosenjith.messos.routes

import com.prosenjith.messos.models.ApiSuccess
import com.prosenjith.messos.models.auth.UserResponse
import com.prosenjith.messos.models.upload.UploadResponse
import com.prosenjith.messos.services.AuthService
import com.prosenjith.messos.services.UploadService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.uploadRoutes(uploadService: UploadService, authService: AuthService) {
    authenticate("jwt-auth") {
        post("/upload") {
            val multipart = call.receiveMultipart()
            val url = uploadService.parseAndStore(multipart, "public/receipts")
            call.respond(HttpStatusCode.OK, ApiSuccess(data = UploadResponse(url)))
        }

        put("/auth/me/avatar") {
            val principal = call.principal<JWTPrincipal>()!!
            val userId = UUID.fromString(principal.payload.subject)
            val multipart = call.receiveMultipart()
            val url = uploadService.parseAndStore(multipart, "public/avatars")
            val user = authService.updateProfileImage(userId, url)
            call.respond(
                HttpStatusCode.OK,
                ApiSuccess(data = UserResponse(user.id.toString(), user.name, user.phoneOrEmail, user.profileImageUrl))
            )
        }
    }
}
