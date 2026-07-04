package com.prosenjith.messos.services

import com.prosenjith.messos.db.tables.CycleStatus
import com.prosenjith.messos.db.tables.Deposits
import com.prosenjith.messos.db.tables.Expenses
import com.prosenjith.messos.db.tables.Meals
import com.prosenjith.messos.db.tables.MessMembers
import com.prosenjith.messos.db.tables.MonthlyCycles
import com.prosenjith.messos.db.tables.Users
import com.prosenjith.messos.util.DepositEntry
import com.prosenjith.messos.util.DuesCalculator
import com.prosenjith.messos.util.ExpenseEntry
import com.prosenjith.messos.util.MealEntry
import com.prosenjith.messos.util.ValidationException
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

data class MemberBalanceRecord(
    val memberUserId: UUID,
    val memberName: String,
    val totalMeals: Double,
    val mealCost: Double,
    val totalDeposited: Double,
    val balance: Double
)

data class DuesRecord(
    val cycleId: UUID,
    val cycleStartDate: String,
    val mealRate: Double,
    val totalExpenses: Double,
    val totalMeals: Double,
    val balances: List<MemberBalanceRecord>
)

class DuesService {

    suspend fun getDuesForCurrentCycle(messId: UUID): DuesRecord =
        newSuspendedTransaction(Dispatchers.IO) {
            val cycle = MonthlyCycles.selectAll()
                .where { (MonthlyCycles.messId eq messId) and (MonthlyCycles.status eq CycleStatus.OPEN) }
                .singleOrNull() ?: throw ValidationException("No open cycle found for this mess")
            val cycleId = cycle[MonthlyCycles.id].value
            val cycleStartDate = cycle[MonthlyCycles.startDate]

            // messMemberId → (userId, name)
            val memberMap = (MessMembers innerJoin Users)
                .selectAll()
                .where { MessMembers.messId eq messId }
                .associate { it[MessMembers.id].value to (it[Users.id].value to it[Users.name]) }

            val nameByUserId = memberMap.values.associate { it.first to it.second }

            val meals = Meals.selectAll()
                .where { (Meals.messId eq messId) and (Meals.date greaterEq cycleStartDate) }
                .mapNotNull { row ->
                    val (userId, _) = memberMap[row[Meals.memberId].value] ?: return@mapNotNull null
                    MealEntry(
                        memberUserId = userId,
                        breakfastCount = row[Meals.breakfastCount].toDouble(),
                        lunchCount = row[Meals.lunchCount].toDouble(),
                        dinnerCount = row[Meals.dinnerCount].toDouble()
                    )
                }

            val expenses = Expenses.selectAll()
                .where { (Expenses.messId eq messId) and (Expenses.cycleId eq cycleId) }
                .map { row -> ExpenseEntry(amount = row[Expenses.amount].toDouble()) }

            val deposits = Deposits.selectAll()
                .where { (Deposits.messId eq messId) and (Deposits.cycleId eq cycleId) }
                .mapNotNull { row ->
                    val (userId, _) = memberMap[row[Deposits.memberId].value] ?: return@mapNotNull null
                    DepositEntry(memberUserId = userId, amount = row[Deposits.amount].toDouble())
                }

            val result = DuesCalculator.calculate(meals, expenses, deposits)

            DuesRecord(
                cycleId = cycleId,
                cycleStartDate = cycleStartDate.toString(),
                mealRate = result.mealRate,
                totalExpenses = result.totalExpenses,
                totalMeals = result.totalMeals,
                balances = result.balances.map { b ->
                    MemberBalanceRecord(
                        memberUserId = b.memberUserId,
                        memberName = nameByUserId[b.memberUserId] ?: "Unknown",
                        totalMeals = b.totalMeals,
                        mealCost = b.mealCost,
                        totalDeposited = b.totalDeposited,
                        balance = b.balance
                    )
                }
            )
        }
}
