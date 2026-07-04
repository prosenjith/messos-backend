package com.prosenjith.messos.routes

import com.prosenjith.messos.models.ApiSuccess
import com.prosenjith.messos.models.meal.DeletedResponse
import com.prosenjith.messos.models.meal.LogMealRequest
import com.prosenjith.messos.models.meal.MealResponse
import com.prosenjith.messos.services.MealRecord
import com.prosenjith.messos.services.MealService
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

fun Route.mealRoutes(mealService: MealService) {
    authenticate("jwt-auth") {
        route("/meals") {
            post {
                val principal = call.principal<JWTPrincipal>()!!
                val callerUserId = UUID.fromString(principal.payload.subject)
                val messId = principal.payload.getClaim("messId").asString()
                    ?.let { UUID.fromString(it) }
                    ?: throw ForbiddenException("You must join a mess before logging meals")

                val req = call.receive<LogMealRequest>()
                val targetUserId = try {
                    UUID.fromString(req.memberId)
                } catch (e: IllegalArgumentException) {
                    throw ValidationException("Invalid memberId")
                }

                val meal = mealService.logMeal(callerUserId, messId, targetUserId, req.date,
                    req.breakfastCount, req.lunchCount, req.dinnerCount)
                call.respond(HttpStatusCode.OK, ApiSuccess(data = meal.toResponse()))
                WebSocketManager.broadcastToMess(messId, WsEvent("MEAL_UPDATED", mapOf("mealId" to meal.id.toString())))
            }

            get {
                val principal = call.principal<JWTPrincipal>()!!
                val messId = principal.payload.getClaim("messId").asString()
                    ?.let { UUID.fromString(it) }
                    ?: throw ForbiddenException("You must join a mess before viewing meals")

                val meals = mealService.getMealsForCurrentCycle(messId)
                call.respond(ApiSuccess(data = meals.map { it.toResponse() }))
            }

            delete("/{id}") {
                val principal = call.principal<JWTPrincipal>()!!
                val callerUserId = UUID.fromString(principal.payload.subject)
                val messId = principal.payload.getClaim("messId").asString()
                    ?.let { UUID.fromString(it) }
                    ?: throw ForbiddenException("You must join a mess before deleting meals")

                val mealId = try {
                    UUID.fromString(call.parameters["id"])
                } catch (e: IllegalArgumentException) {
                    throw ValidationException("Invalid meal id")
                }

                mealService.deleteMeal(callerUserId, messId, mealId)
                call.respond(ApiSuccess(data = DeletedResponse()))
                WebSocketManager.broadcastToMess(messId, WsEvent("MEAL_DELETED", mapOf("mealId" to mealId.toString())))
            }
        }
    }
}

private fun MealRecord.toResponse() = MealResponse(
    id = id.toString(),
    memberId = memberUserId.toString(),
    memberName = memberName,
    date = date,
    breakfastCount = breakfastCount,
    lunchCount = lunchCount,
    dinnerCount = dinnerCount,
    updatedAt = updatedAt
)
