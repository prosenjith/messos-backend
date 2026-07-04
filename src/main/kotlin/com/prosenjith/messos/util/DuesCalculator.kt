package com.prosenjith.messos.util

import java.util.UUID

data class MealEntry(
    val memberUserId: UUID,
    val breakfastCount: Double,
    val lunchCount: Double,
    val dinnerCount: Double
)

data class ExpenseEntry(val amount: Double)

data class DepositEntry(val memberUserId: UUID, val amount: Double)

data class MemberBalance(
    val memberUserId: UUID,
    val totalMeals: Double,
    val mealCost: Double,
    val totalDeposited: Double,
    val balance: Double
)

data class DuesResult(
    val mealRate: Double,
    val totalExpenses: Double,
    val totalMeals: Double,
    val balances: List<MemberBalance>
)

object DuesCalculator {

    fun calculate(
        meals: List<MealEntry>,
        expenses: List<ExpenseEntry>,
        deposits: List<DepositEntry>
    ): DuesResult {
        val totalExpenses = expenses.sumOf { it.amount }
        val totalMeals = meals.sumOf { it.breakfastCount + it.lunchCount + it.dinnerCount }
        val mealRate = if (totalMeals == 0.0) 0.0 else totalExpenses / totalMeals

        val allMemberIds = (meals.map { it.memberUserId } + deposits.map { it.memberUserId }).distinct()

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
                totalDeposited = memberDeposits,
                balance = memberDeposits - mealCost
            )
        }

        return DuesResult(
            mealRate = mealRate,
            totalExpenses = totalExpenses,
            totalMeals = totalMeals,
            balances = balances
        )
    }
}
