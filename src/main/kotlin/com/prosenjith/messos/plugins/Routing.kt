package com.prosenjith.messos.plugins

import com.prosenjith.messos.config.AppConfig
import com.prosenjith.messos.routes.authRoutes
import com.prosenjith.messos.services.AuthService
import io.ktor.server.application.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*

fun Application.configureRouting(config: AppConfig) {
    val authService = AuthService()

    routing {
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")

        route("/api/v1") {
            authRoutes(authService, config.jwt)
        }
    }
}
