package com.prosenjith.messos.routes

import com.prosenjith.messos.config.JwtConfig
import com.prosenjith.messos.models.ApiSuccess
import com.prosenjith.messos.models.auth.LoginRequest
import com.prosenjith.messos.models.auth.RefreshRequest
import com.prosenjith.messos.models.auth.SignupRequest
import com.prosenjith.messos.models.auth.TokenResponse
import com.prosenjith.messos.models.auth.UserResponse
import com.prosenjith.messos.services.AuthService
import com.prosenjith.messos.util.JwtUtils
import com.prosenjith.messos.util.NotFoundException
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.authRoutes(authService: AuthService, jwtConfig: JwtConfig) {
    route("/auth") {
        post("/signup") {
            val req = call.receive<SignupRequest>()
            val user = authService.signup(req.name, req.phoneOrEmail, req.password)
            val token = JwtUtils.generateToken(jwtConfig, user.id)
            val refreshToken = authService.issueRefreshToken(user.id, jwtConfig)
            call.respond(HttpStatusCode.Created, ApiSuccess(data = TokenResponse(
                userId = user.id.toString(),
                token = token,
                refreshToken = refreshToken
            )))
        }

        post("/login") {
            val req = call.receive<LoginRequest>()
            val result = authService.login(req.phoneOrEmail, req.password)
            val token = JwtUtils.generateToken(jwtConfig, result.userId, result.messId, result.role)
            val refreshToken = authService.issueRefreshToken(result.userId, jwtConfig)
            call.respond(HttpStatusCode.OK, ApiSuccess(data = TokenResponse(
                userId = result.userId.toString(),
                token = token,
                refreshToken = refreshToken,
                messId = result.messId?.toString(),
                role = result.role
            )))
        }

        post("/refresh") {
            val req = call.receive<RefreshRequest>()
            val (newRefreshToken, loginResult) = authService.rotateRefreshToken(req.refreshToken, jwtConfig)
            val token = JwtUtils.generateToken(jwtConfig, loginResult.userId, loginResult.messId, loginResult.role)
            call.respond(HttpStatusCode.OK, ApiSuccess(data = TokenResponse(
                userId = loginResult.userId.toString(),
                token = token,
                refreshToken = newRefreshToken,
                messId = loginResult.messId?.toString(),
                role = loginResult.role
            )))
        }

        post("/logout") {
            val req = call.receive<RefreshRequest>()
            authService.revokeRefreshToken(req.refreshToken)
            call.respond(HttpStatusCode.OK, ApiSuccess(data = "Logged out successfully"))
        }

        authenticate("jwt-auth") {
            get("/me") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = UUID.fromString(principal.payload.subject)
                val user = authService.findById(userId)
                    ?: throw NotFoundException("User not found")
                call.respond(
                    HttpStatusCode.OK,
                    ApiSuccess(data = UserResponse(user.id.toString(), user.name, user.phoneOrEmail, user.profileImageUrl))
                )
            }
        }
    }
}
