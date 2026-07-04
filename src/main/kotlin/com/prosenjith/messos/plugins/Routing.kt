package com.prosenjith.messos.plugins

import com.prosenjith.messos.config.AppConfig
import com.prosenjith.messos.routes.authRoutes
import com.prosenjith.messos.routes.messRoutes
import com.prosenjith.messos.services.AuthService
import com.prosenjith.messos.services.MessService
import io.ktor.server.application.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*

fun Application.configureRouting(config: AppConfig) {
    val authService = AuthService()
    val messService = MessService()

    routing {
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")

        route("/api/v1") {
            authRoutes(authService, config.jwt)
            messRoutes(messService, config.jwt)
        }
    }
}
