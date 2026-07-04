package com.prosenjith.messos.plugins

import com.prosenjith.messos.config.AppConfig
import com.prosenjith.messos.routes.authRoutes
import com.prosenjith.messos.routes.cycleRoutes
import com.prosenjith.messos.routes.depositRoutes
import com.prosenjith.messos.routes.duesRoutes
import com.prosenjith.messos.routes.expenseRoutes
import com.prosenjith.messos.routes.mealRoutes
import com.prosenjith.messos.routes.messRoutes
import com.prosenjith.messos.services.AuthService
import com.prosenjith.messos.services.CycleService
import com.prosenjith.messos.services.DepositService
import com.prosenjith.messos.services.DuesService
import com.prosenjith.messos.services.ExpenseService
import com.prosenjith.messos.services.MealService
import com.prosenjith.messos.services.MessService
import io.ktor.server.application.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*

fun Application.configureRouting(config: AppConfig) {
    val authService = AuthService()
    val messService = MessService()
    val mealService = MealService()
    val expenseService = ExpenseService()
    val depositService = DepositService()
    val duesService = DuesService()
    val cycleService = CycleService()

    routing {
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")

        route("/api/v1") {
            authRoutes(authService, config.jwt)
            messRoutes(messService, config.jwt)
            mealRoutes(mealService)
            expenseRoutes(expenseService)
            depositRoutes(depositService)
            duesRoutes(duesService)
            cycleRoutes(cycleService)
        }
    }
}
