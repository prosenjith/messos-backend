package com.prosenjith.messos.plugins

import com.prosenjith.messos.models.ApiError
import com.prosenjith.messos.models.ApiFailure
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiFailure(error = ApiError("INTERNAL_ERROR", "An unexpected error occurred"))
            )
        }
    }
}
