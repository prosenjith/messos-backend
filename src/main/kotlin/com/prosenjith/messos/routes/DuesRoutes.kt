package com.prosenjith.messos.routes

import com.prosenjith.messos.models.ApiSuccess
import com.prosenjith.messos.models.dues.DuesResponse
import com.prosenjith.messos.models.dues.MemberBalanceResponse
import com.prosenjith.messos.services.DuesRecord
import com.prosenjith.messos.services.DuesService
import com.prosenjith.messos.util.ForbiddenException
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.duesRoutes(duesService: DuesService) {
    authenticate("jwt-auth") {
        get("/dues") {
            val principal = call.principal<JWTPrincipal>()!!
            val messId = principal.payload.getClaim("messId").asString()
                ?.let { UUID.fromString(it) }
                ?: throw ForbiddenException("You must join a mess before viewing dues")

            val dues = duesService.getDuesForCurrentCycle(messId)
            call.respond(ApiSuccess(data = dues.toResponse()))
        }
    }
}

private fun DuesRecord.toResponse() = DuesResponse(
    cycleId = cycleId.toString(),
    cycleStartDate = cycleStartDate,
    mealRate = mealRate,
    totalExpenses = totalExpenses,
    totalUtilityExpense = totalUtilityExpense,
    totalMeals = totalMeals,
    balances = balances.map {
        MemberBalanceResponse(
            memberId = it.memberUserId.toString(),
            memberName = it.memberName,
            totalMeals = it.totalMeals,
            mealCost = it.mealCost,
            utilityShare = it.utilityShare,
            totalDeposited = it.totalDeposited,
            balance = it.balance
        )
    }
)
