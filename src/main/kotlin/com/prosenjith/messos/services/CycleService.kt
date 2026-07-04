package com.prosenjith.messos.services

import com.prosenjith.messos.db.tables.CycleStatus
import com.prosenjith.messos.db.tables.CycleSummaries
import com.prosenjith.messos.db.tables.Deposits
import com.prosenjith.messos.db.tables.Expenses
import com.prosenjith.messos.db.tables.MemberRole
import com.prosenjith.messos.db.tables.Meals
import com.prosenjith.messos.db.tables.MessMembers
import com.prosenjith.messos.db.tables.MonthlyCycles
import com.prosenjith.messos.db.tables.Users
import com.prosenjith.messos.util.CycleAlreadyClosedException
import com.prosenjith.messos.util.DepositEntry
import com.prosenjith.messos.util.DuesCalculator
import com.prosenjith.messos.util.ExpenseEntry
import com.prosenjith.messos.util.ForbiddenException
import com.prosenjith.messos.util.MealEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

data class CycleMemberSummaryRecord(
    val memberUserId: UUID,
    val memberName: String,
    val totalMeals: Double,
    val mealCost: Double,
    val totalDeposited: Double,
    val balance: Double
)

data class CycleCloseRecord(
    val cycleId: UUID,
    val startDate: String,
    val endDate: String,
    val mealRate: Double,
    val totalExpenses: Double,
    val totalMeals: Double,
    val closedAt: String,
    val balances: List<CycleMemberSummaryRecord>,
    val newCycleId: UUID,
    val newCycleStartDate: String
)

data class CycleHistoryRecord(
    val cycleId: UUID,
    val startDate: String,
    val endDate: String,
    val mealRate: Double,
    val closedAt: String,
    val balances: List<CycleMemberSummaryRecord>
)

class CycleService {

    suspend fun closeCycle(callerUserId: UUID, messId: UUID): CycleCloseRecord =
        newSuspendedTransaction(Dispatchers.IO) {
            val callerMember = MessMembers.selectAll()
                .where { (MessMembers.messId eq messId) and (MessMembers.userId eq callerUserId) }
                .singleOrNull() ?: throw ForbiddenException("You are not a member of this mess")
            if (callerMember[MessMembers.role] != MemberRole.MANAGER) {
                throw ForbiddenException("Only the manager can close the cycle")
            }

            val cycle = MonthlyCycles.selectAll()
                .where { (MonthlyCycles.messId eq messId) and (MonthlyCycles.status eq CycleStatus.OPEN) }
                .singleOrNull() ?: throw CycleAlreadyClosedException("There is no open cycle to close")
            val cycleId = cycle[MonthlyCycles.id].value
            val cycleStartDate = cycle[MonthlyCycles.startDate]

            val memberRows = (MessMembers innerJoin Users)
                .selectAll()
                .where { MessMembers.messId eq messId }
                .toList()
            // messMemberId → userId
            val userIdByMemberId = memberRows.associate { it[MessMembers.id].value to it[Users.id].value }
            // userId → (messMemberId, name)
            val memberDataByUserId = memberRows.associate {
                it[Users.id].value to (it[MessMembers.id].value to it[Users.name])
            }

            val meals = Meals.selectAll()
                .where { (Meals.messId eq messId) and (Meals.date greaterEq cycleStartDate) }
                .mapNotNull { row ->
                    val userId = userIdByMemberId[row[Meals.memberId].value] ?: return@mapNotNull null
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
                    val userId = userIdByMemberId[row[Deposits.memberId].value] ?: return@mapNotNull null
                    DepositEntry(memberUserId = userId, amount = row[Deposits.amount].toDouble())
                }

            val result = DuesCalculator.calculate(meals, expenses, deposits)

            val today = Clock.System.todayIn(TimeZone.UTC)
            val closedAt = Clock.System.now()

            // Persist per-member balances
            result.balances.forEach { balance ->
                val (messMemberId, memberName) = memberDataByUserId[balance.memberUserId] ?: return@forEach
                CycleSummaries.insert {
                    it[CycleSummaries.cycleId]        = cycleId
                    it[CycleSummaries.memberId]        = messMemberId
                    it[CycleSummaries.memberName]      = memberName
                    it[CycleSummaries.totalMeals]      = balance.totalMeals.toBigDecimal()
                    it[CycleSummaries.mealCost]        = balance.mealCost.toBigDecimal()
                    it[CycleSummaries.totalDeposited]  = balance.totalDeposited.toBigDecimal()
                    it[CycleSummaries.balance]         = balance.balance.toBigDecimal()
                }
            }

            // Close the cycle
            MonthlyCycles.update({ MonthlyCycles.id eq cycleId }) {
                it[MonthlyCycles.status]             = CycleStatus.CLOSED
                it[MonthlyCycles.endDate]            = today
                it[MonthlyCycles.mealRateSnapshot]   = result.mealRate.toBigDecimal()
                it[MonthlyCycles.closedAt]           = closedAt
            }

            // Open a new cycle starting tomorrow
            val newStartDate = today.plus(1, DateTimeUnit.DAY)
            val newCycleRow = MonthlyCycles.insert {
                it[MonthlyCycles.messId]    = messId
                it[MonthlyCycles.startDate] = newStartDate
                it[MonthlyCycles.status]    = CycleStatus.OPEN
            }
            val newCycleId = newCycleRow[MonthlyCycles.id].value

            val nameByUserId = memberDataByUserId.mapValues { it.value.second }
            CycleCloseRecord(
                cycleId        = cycleId,
                startDate      = cycleStartDate.toString(),
                endDate        = today.toString(),
                mealRate       = result.mealRate,
                totalExpenses  = result.totalExpenses,
                totalMeals     = result.totalMeals,
                closedAt       = closedAt.toString(),
                balances       = result.balances.map { b ->
                    CycleMemberSummaryRecord(
                        memberUserId   = b.memberUserId,
                        memberName     = nameByUserId[b.memberUserId] ?: "Unknown",
                        totalMeals     = b.totalMeals,
                        mealCost       = b.mealCost,
                        totalDeposited = b.totalDeposited,
                        balance        = b.balance
                    )
                },
                newCycleId        = newCycleId,
                newCycleStartDate = newStartDate.toString()
            )
        }

    suspend fun getCycleHistory(messId: UUID): List<CycleHistoryRecord> =
        newSuspendedTransaction(Dispatchers.IO) {
            val closedCycles = MonthlyCycles.selectAll()
                .where { (MonthlyCycles.messId eq messId) and (MonthlyCycles.status eq CycleStatus.CLOSED) }
                .orderBy(MonthlyCycles.startDate, SortOrder.DESC)
                .toList()

            // messMemberId → userId
            val userIdByMemberId = MessMembers.selectAll()
                .where { MessMembers.messId eq messId }
                .associate { it[MessMembers.id].value to it[MessMembers.userId].value }

            closedCycles.map { cycleRow ->
                val cycleId = cycleRow[MonthlyCycles.id].value

                val balances = CycleSummaries.selectAll()
                    .where { CycleSummaries.cycleId eq cycleId }
                    .map { row ->
                        val messMemberId = row[CycleSummaries.memberId].value
                        CycleMemberSummaryRecord(
                            memberUserId   = userIdByMemberId[messMemberId] ?: messMemberId,
                            memberName     = row[CycleSummaries.memberName],
                            totalMeals     = row[CycleSummaries.totalMeals].toDouble(),
                            mealCost       = row[CycleSummaries.mealCost].toDouble(),
                            totalDeposited = row[CycleSummaries.totalDeposited].toDouble(),
                            balance        = row[CycleSummaries.balance].toDouble()
                        )
                    }

                CycleHistoryRecord(
                    cycleId   = cycleId,
                    startDate = cycleRow[MonthlyCycles.startDate].toString(),
                    endDate   = cycleRow[MonthlyCycles.endDate].toString(),
                    mealRate  = cycleRow[MonthlyCycles.mealRateSnapshot]?.toDouble() ?: 0.0,
                    closedAt  = cycleRow[MonthlyCycles.closedAt].toString(),
                    balances  = balances
                )
            }
        }
}
