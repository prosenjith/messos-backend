package com.prosenjith.messos.db.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption

object CycleSummaries : UUIDTable("cycle_summaries") {
    val cycleId     = reference("cycle_id",  MonthlyCycles, onDelete = ReferenceOption.CASCADE)
    val memberId    = reference("member_id", MessMembers,   onDelete = ReferenceOption.CASCADE)
    val memberName  = varchar("member_name", 100)
    val totalMeals  = decimal("total_meals",  8, 2)
    val mealCost    = decimal("meal_cost",    10, 2)
    val utilityShare = decimal("utility_share", 10, 2)
    val totalDeposited = decimal("total_deposited", 10, 2)
    val balance     = decimal("balance", 10, 2)
}
