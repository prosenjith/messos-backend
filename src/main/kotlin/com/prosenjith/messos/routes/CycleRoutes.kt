package com.prosenjith.messos.routes

import com.prosenjith.messos.models.ApiSuccess
import com.prosenjith.messos.models.cycle.CycleCloseResponse
import com.prosenjith.messos.models.cycle.CycleHistoryItem
import com.prosenjith.messos.models.cycle.CycleMemberSummary
import com.prosenjith.messos.services.CycleCloseRecord
import com.prosenjith.messos.services.CycleHistoryRecord
import com.prosenjith.messos.services.CycleMemberSummaryRecord
import com.prosenjith.messos.services.CycleService
import com.prosenjith.messos.models.ws.WsEvent
import com.prosenjith.messos.util.ForbiddenException
import com.prosenjith.messos.util.WebSocketManager
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.cycleRoutes(cycleService: CycleService) {
    authenticate("jwt-auth") {
        route("/cycle") {
            post("/close") {
                val principal = call.principal<JWTPrincipal>()!!
                val callerUserId = UUID.fromString(principal.payload.subject)
                val messId = principal.payload.getClaim("messId").asString()
                    ?.let { UUID.fromString(it) }
                    ?: throw ForbiddenException("You must join a mess before closing a cycle")

                val record = cycleService.closeCycle(callerUserId, messId)
                call.respond(HttpStatusCode.OK, ApiSuccess(data = record.toResponse()))
                WebSocketManager.broadcastToMess(messId, WsEvent("CYCLE_CLOSED", mapOf(
                    "cycleId" to record.cycleId.toString(),
                    "newCycleId" to record.newCycleId.toString()
                )))
            }

            get("/history") {
                val principal = call.principal<JWTPrincipal>()!!
                val messId = principal.payload.getClaim("messId").asString()
                    ?.let { UUID.fromString(it) }
                    ?: throw ForbiddenException("You must join a mess before viewing cycle history")

                val history = cycleService.getCycleHistory(messId)
                call.respond(ApiSuccess(data = history.map { it.toResponse() }))
            }
        }
    }
}

private fun CycleMemberSummaryRecord.toResponse() = CycleMemberSummary(
    memberId       = memberUserId.toString(),
    memberName     = memberName,
    totalMeals     = totalMeals,
    mealCost       = mealCost,
    totalDeposited = totalDeposited,
    balance        = balance
)

private fun CycleCloseRecord.toResponse() = CycleCloseResponse(
    cycleId           = cycleId.toString(),
    startDate         = startDate,
    endDate           = endDate,
    mealRate          = mealRate,
    totalExpenses     = totalExpenses,
    totalMeals        = totalMeals,
    closedAt          = closedAt,
    balances          = balances.map { it.toResponse() },
    newCycleId        = newCycleId.toString(),
    newCycleStartDate = newCycleStartDate
)

private fun CycleHistoryRecord.toResponse() = CycleHistoryItem(
    cycleId   = cycleId.toString(),
    startDate = startDate,
    endDate   = endDate,
    mealRate  = mealRate,
    closedAt  = closedAt,
    balances  = balances.map { it.toResponse() }
)
