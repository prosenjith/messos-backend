package com.prosenjith.messos.models.cycle

import kotlinx.serialization.Serializable

@Serializable
data class CycleMemberSummary(
    val memberId: String,
    val memberName: String,
    val totalMeals: Double,
    val mealCost: Double,
    val utilityShare: Double,
    val totalDeposited: Double,
    val balance: Double
)

@Serializable
data class CycleCloseResponse(
    val cycleId: String,
    val startDate: String,
    val endDate: String,
    val mealRate: Double,
    val totalExpenses: Double,
    val totalUtilityExpense: Double,
    val totalMeals: Double,
    val closedAt: String,
    val balances: List<CycleMemberSummary>,
    val newCycleId: String,
    val newCycleStartDate: String
)

@Serializable
data class CycleHistoryItem(
    val cycleId: String,
    val startDate: String,
    val endDate: String,
    val mealRate: Double,
    val totalExpenses: Double,
    val totalUtilityExpense: Double,
    val totalMeals: Double,
    val closedAt: String,
    val balances: List<CycleMemberSummary>
)

@Serializable
data class CycleHistoryPageResponse(
    val items: List<CycleHistoryItem>,
    val page: Int,
    val size: Int,
    val total: Long,
    val hasMore: Boolean
)
