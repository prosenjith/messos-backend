package com.prosenjith.messos.routes

import com.prosenjith.messos.models.ApiSuccess
import com.prosenjith.messos.models.deposit.AddDepositRequest
import com.prosenjith.messos.models.deposit.DepositResponse
import com.prosenjith.messos.models.meal.DeletedResponse
import com.prosenjith.messos.services.DepositRecord
import com.prosenjith.messos.services.DepositService
import com.prosenjith.messos.util.ForbiddenException
import com.prosenjith.messos.util.ValidationException
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.depositRoutes(depositService: DepositService) {
    authenticate("jwt-auth") {
        route("/deposits") {
            post {
                val principal = call.principal<JWTPrincipal>()!!
                val callerUserId = UUID.fromString(principal.payload.subject)
                val messId = principal.payload.getClaim("messId").asString()
                    ?.let { UUID.fromString(it) }
                    ?: throw ForbiddenException("You must join a mess before recording deposits")

                val req = call.receive<AddDepositRequest>()
                val targetUserId = try {
                    UUID.fromString(req.memberId)
                } catch (e: IllegalArgumentException) {
                    throw ValidationException("Invalid memberId")
                }
                val deposit = depositService.addDeposit(callerUserId, messId, targetUserId,
                    req.amount, req.date)
                call.respond(HttpStatusCode.Created, ApiSuccess(data = deposit.toResponse()))
            }

            get {
                val principal = call.principal<JWTPrincipal>()!!
                val messId = principal.payload.getClaim("messId").asString()
                    ?.let { UUID.fromString(it) }
                    ?: throw ForbiddenException("You must join a mess before viewing deposits")

                val deposits = depositService.getDepositsForCurrentCycle(messId)
                call.respond(ApiSuccess(data = deposits.map { it.toResponse() }))
            }

            delete("/{id}") {
                val principal = call.principal<JWTPrincipal>()!!
                val callerUserId = UUID.fromString(principal.payload.subject)
                val messId = principal.payload.getClaim("messId").asString()
                    ?.let { UUID.fromString(it) }
                    ?: throw ForbiddenException("You must join a mess before deleting deposits")

                val depositId = try {
                    UUID.fromString(call.parameters["id"])
                } catch (e: IllegalArgumentException) {
                    throw ValidationException("Invalid deposit id")
                }
                depositService.deleteDeposit(callerUserId, messId, depositId)
                call.respond(ApiSuccess(data = DeletedResponse()))
            }
        }
    }
}

private fun DepositRecord.toResponse() = DepositResponse(
    id = id.toString(),
    memberId = memberUserId.toString(),
    memberName = memberName,
    amount = amount,
    date = date,
    loggedBy = loggedByUserId.toString(),
    loggedByName = loggedByName,
    createdAt = createdAt
)
