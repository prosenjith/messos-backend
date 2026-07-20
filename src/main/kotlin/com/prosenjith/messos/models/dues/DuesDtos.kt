package com.prosenjith.messos.models.dues

import kotlinx.serialization.Serializable

@Serializable
data class MemberBalanceResponse(
    val memberId: String,
    val memberName: String,
    val totalMeals: Double,
    val mealCost: Double,
    val utilityShare: Double,
    val totalDeposited: Double,
    val balance: Double
)

@Serializable
data class DuesResponse(
    val cycleId: String,
    val cycleStartDate: String,
    val mealRate: Double,
    val totalExpenses: Double,
    val totalUtilityExpense: Double,
    val totalMeals: Double,
    val balances: List<MemberBalanceResponse>
)
