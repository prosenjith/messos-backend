package com.prosenjith.messos.db.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

enum class CycleStatus { OPEN, CLOSED }

object MonthlyCycles : UUIDTable("monthly_cycles") {
    val messId = reference("mess_id", Messes, onDelete = ReferenceOption.CASCADE)
    val startDate = date("start_date")
    val endDate = date("end_date").nullable()
    val status = enumerationByName("status", 10, CycleStatus::class).default(CycleStatus.OPEN)
    val mealRateSnapshot    = decimal("meal_rate_snapshot", 10, 2).nullable()
    val totalExpenses       = decimal("total_expenses", 10, 2).nullable()
    val totalUtilityExpense = decimal("total_utility_expense", 10, 2).nullable()
    val totalMeals          = decimal("total_meals", 8, 2).nullable()
    val closedAt = timestamp("closed_at").nullable()
}
