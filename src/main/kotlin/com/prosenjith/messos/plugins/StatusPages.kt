package com.prosenjith.messos.plugins

import com.prosenjith.messos.models.ApiError
import com.prosenjith.messos.models.ApiFailure
import com.prosenjith.messos.util.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<DuplicateEntryException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, ApiFailure(error = ApiError("DUPLICATE_ENTRY", cause.message ?: "Duplicate entry")))
        }
        exception<UnauthorizedException> { call, cause ->
            call.respond(HttpStatusCode.Unauthorized, ApiFailure(error = ApiError("UNAUTHORIZED", cause.message ?: "Unauthorized")))
        }
        exception<ForbiddenException> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, ApiFailure(error = ApiError("FORBIDDEN", cause.message ?: "Forbidden")))
        }
        exception<NotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ApiFailure(error = ApiError("NOT_FOUND", cause.message ?: "Not found")))
        }
        exception<ValidationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ApiFailure(error = ApiError("VALIDATION_ERROR", cause.message ?: "Validation error")))
        }
        exception<InvalidJoinCodeException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ApiFailure(error = ApiError("INVALID_JOIN_CODE", cause.message ?: "Invalid join code")))
        }
        exception<CycleAlreadyClosedException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ApiFailure(error = ApiError("CYCLE_ALREADY_CLOSED", cause.message ?: "No open cycle found")))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiFailure(error = ApiError("INTERNAL_ERROR", "An unexpected error occurred"))
            )
        }
    }
}
