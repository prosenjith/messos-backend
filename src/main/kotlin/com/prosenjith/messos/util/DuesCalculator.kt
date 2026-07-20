package com.prosenjith.messos.util

import java.util.UUID

enum class ExpenseCategory { BAZAAR, UTILITY }

data class MealEntry(
    val memberUserId: UUID,
    val breakfastCount: Double,
    val lunchCount: Double,
    val dinnerCount: Double
)

data class ExpenseEntry(
    val amount: Double,
    val category: ExpenseCategory = ExpenseCategory.BAZAAR
)

data class DepositEntry(val memberUserId: UUID, val amount: Double)

data class MemberBalance(
    val memberUserId: UUID,
    val totalMeals: Double,
    val mealCost: Double,
    val utilityShare: Double,
    val totalDeposited: Double,
    val balance: Double
)

data class DuesResult(
    val mealRate: Double,
    val totalExpenses: Double,
    val totalUtilityExpense: Double,
    val totalMeals: Double,
    val balances: List<MemberBalance>
)

object DuesCalculator {

    fun calculate(
        meals: List<MealEntry>,
        expenses: List<ExpenseEntry>,
        deposits: List<DepositEntry>,
        allMemberUserIds: List<UUID> = emptyList()
    ): DuesResult {
        val totalBazaarExpense = expenses.filter { it.category == ExpenseCategory.BAZAAR }.sumOf { it.amount }
        val totalUtilityExpense = expenses.filter { it.category == ExpenseCategory.UTILITY }.sumOf { it.amount }
        val totalExpenses = totalBazaarExpense + totalUtilityExpense

        val totalMeals = meals.sumOf { it.breakfastCount + it.lunchCount + it.dinnerCount }
        val mealRate = if (totalMeals == 0.0) 0.0 else totalBazaarExpense / totalMeals

        val allMemberIds = (allMemberUserIds +
            meals.map { it.memberUserId } +
            deposits.map { it.memberUserId }).distinct()
        val activeMemberCount = allMemberIds.size
        val utilityShare = if (activeMemberCount == 0) 0.0 else totalUtilityExpense / activeMemberCount

        val mealsByMember = meals.groupBy { it.memberUserId }
        val depositsByMember = deposits.groupBy { it.memberUserId }

        val balances = allMemberIds.map { memberId ->
            val memberMeals = mealsByMember[memberId]
                ?.sumOf { it.breakfastCount + it.lunchCount + it.dinnerCount } ?: 0.0
            val memberDeposits = depositsByMember[memberId]?.sumOf { it.amount } ?: 0.0
            val mealCost = memberMeals * mealRate
            MemberBalance(
                memberUserId = memberId,
                totalMeals = memberMeals,
                mealCost = mealCost,
                utilityShare = utilityShare,
                totalDeposited = memberDeposits,
                balance = memberDeposits - mealCost - utilityShare
            )
        }

        return DuesResult(
            mealRate = mealRate,
            totalExpenses = totalExpenses,
            totalUtilityExpense = totalUtilityExpense,
            totalMeals = totalMeals,
            balances = balances
        )
    }
}
