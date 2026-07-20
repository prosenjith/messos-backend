package com.prosenjith.messos.routes

import com.prosenjith.messos.models.ApiSuccess
import com.prosenjith.messos.models.expense.AddExpenseRequest
import com.prosenjith.messos.models.expense.ExpenseResponse
import com.prosenjith.messos.models.meal.DeletedResponse
import com.prosenjith.messos.services.ExpenseRecord
import com.prosenjith.messos.services.ExpenseService
import com.prosenjith.messos.models.ws.WsEvent
import com.prosenjith.messos.util.ForbiddenException
import com.prosenjith.messos.util.ValidationException
import com.prosenjith.messos.util.WebSocketManager
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.expenseRoutes(expenseService: ExpenseService) {
    authenticate("jwt-auth") {
        route("/expenses") {
            post {
                val principal = call.principal<JWTPrincipal>()!!
                val callerUserId = UUID.fromString(principal.payload.subject)
                val messId = principal.payload.getClaim("messId").asString()
                    ?.let { UUID.fromString(it) }
                    ?: throw ForbiddenException("You must join a mess before logging expenses")

                val req = call.receive<AddExpenseRequest>()
                val expense = expenseService.addExpense(callerUserId, messId, req.amount, req.date,
                    req.note, req.receiptPhotoUrl, req.category)
                call.respond(HttpStatusCode.Created, ApiSuccess(data = expense.toResponse()))
                WebSocketManager.broadcastToMess(messId, WsEvent("EXPENSE_ADDED", mapOf("expenseId" to expense.id.toString())))
            }

            get {
                val principal = call.principal<JWTPrincipal>()!!
                val messId = principal.payload.getClaim("messId").asString()
                    ?.let { UUID.fromString(it) }
                    ?: throw ForbiddenException("You must join a mess before viewing expenses")

                val expenses = expenseService.getExpensesForCurrentCycle(messId)
                call.respond(ApiSuccess(data = expenses.map { it.toResponse() }))
            }

            delete("/{id}") {
                val principal = call.principal<JWTPrincipal>()!!
                val callerUserId = UUID.fromString(principal.payload.subject)
                val messId = principal.payload.getClaim("messId").asString()
                    ?.let { UUID.fromString(it) }
                    ?: throw ForbiddenException("You must join a mess before deleting expenses")

                val expenseId = try {
                    UUID.fromString(call.parameters["id"])
                } catch (e: IllegalArgumentException) {
                    throw ValidationException("Invalid expense id")
                }
                expenseService.deleteExpense(callerUserId, messId, expenseId)
                call.respond(ApiSuccess(data = DeletedResponse()))
                WebSocketManager.broadcastToMess(messId, WsEvent("EXPENSE_DELETED", mapOf("expenseId" to expenseId.toString())))
            }
        }
    }
}

private fun ExpenseRecord.toResponse() = ExpenseResponse(
    id = id.toString(),
    amount = amount,
    date = date,
    note = note,
    receiptPhotoUrl = receiptPhotoUrl,
    loggedBy = loggedByUserId.toString(),
    loggedByName = loggedByName,
    category = category.name,
    createdAt = createdAt
)
