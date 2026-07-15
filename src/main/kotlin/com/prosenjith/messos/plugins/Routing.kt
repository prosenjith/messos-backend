package com.prosenjith.messos.plugins

import com.prosenjith.messos.config.AppConfig
import com.prosenjith.messos.routes.authRoutes
import com.prosenjith.messos.routes.cycleRoutes
import com.prosenjith.messos.routes.depositRoutes
import com.prosenjith.messos.routes.duesRoutes
import com.prosenjith.messos.routes.expenseRoutes
import com.prosenjith.messos.routes.mealRoutes
import com.prosenjith.messos.routes.messRoutes
import com.prosenjith.messos.routes.noticeRoutes
import com.prosenjith.messos.routes.uploadRoutes
import com.prosenjith.messos.routes.webSocketRoute
import com.prosenjith.messos.services.AuthService
import com.prosenjith.messos.services.CycleService
import com.prosenjith.messos.services.DepositService
import com.prosenjith.messos.services.DuesService
import com.prosenjith.messos.services.ExpenseService
import com.prosenjith.messos.services.MealService
import com.prosenjith.messos.services.MessService
import com.prosenjith.messos.services.NoticeService
import com.prosenjith.messos.services.UploadService
import com.prosenjith.messos.util.S3FileStorageService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(config: AppConfig) {
    val authService = AuthService()
    val messService = MessService()
    val mealService = MealService()
    val expenseService = ExpenseService()
    val depositService = DepositService()
    val duesService = DuesService()
    val cycleService = CycleService()
    val noticeService = NoticeService()
    val bucket = System.getenv("AWS_S3_BUCKET") ?: error("AWS_S3_BUCKET env var not set")
    val region = System.getenv("AWS_REGION") ?: error("AWS_REGION env var not set")
    val fileStorageService = S3FileStorageService(bucket, region)
    val uploadService = UploadService(fileStorageService)

    routing {
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")

        route("/api/v1") {
            authRoutes(authService, config.jwt)
            messRoutes(messService, config.jwt)
            mealRoutes(mealService)
            expenseRoutes(expenseService)
            depositRoutes(depositService)
            duesRoutes(duesService)
            cycleRoutes(cycleService)
            noticeRoutes(noticeService)
            uploadRoutes(uploadService, authService)
            webSocketRoute(config.jwt)
        }
    }
}
