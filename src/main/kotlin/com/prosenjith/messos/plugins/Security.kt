package com.prosenjith.messos.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.prosenjith.messos.config.AppConfig
import com.prosenjith.messos.models.ApiError
import com.prosenjith.messos.models.ApiFailure
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

fun Application.configureSecurity(config: AppConfig) {
    install(Authentication) {
        jwt("jwt-auth") {
            realm = "messos-api"
            verifier(
                JWT.require(Algorithm.HMAC256(config.jwt.secret))
                    .withIssuer(config.jwt.issuer)
                    .withAudience(config.jwt.audience)
                    .build()
            )
            validate { credential ->
                if (credential.payload.subject != null) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiFailure(error = ApiError("UNAUTHORIZED", "Missing or invalid token"))
                )
            }
        }
    }
}
