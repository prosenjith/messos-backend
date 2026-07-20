package com.prosenjith.messos.services

import com.prosenjith.messos.db.tables.CycleStatus
import com.prosenjith.messos.db.tables.Expenses
import com.prosenjith.messos.db.tables.MemberRole
import com.prosenjith.messos.db.tables.MessMembers
import com.prosenjith.messos.db.tables.MonthlyCycles
import com.prosenjith.messos.db.tables.Users
import com.prosenjith.messos.util.ExpenseCategory
import com.prosenjith.messos.util.ForbiddenException
import com.prosenjith.messos.util.NotFoundException
import com.prosenjith.messos.util.ValidationException
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

data class ExpenseRecord(
    val id: UUID,
    val amount: Double,
    val date: String,
    val note: String?,
    val receiptPhotoUrl: String?,
    val loggedByUserId: UUID,
    val loggedByName: String,
    val category: ExpenseCategory,
    val createdAt: String
)

class ExpenseService {

    suspend fun addExpense(
        callerUserId: UUID,
        messId: UUID,
        amount: Double,
        dateStr: String,
        note: String?,
        receiptPhotoUrl: String?,
        categoryStr: String
    ): ExpenseRecord {
        if (amount <= 0) throw ValidationException("Expense amount must be greater than zero")
        val date = try {
            LocalDate.parse(dateStr)
        } catch (e: Exception) {
            throw ValidationException("Invalid date format. Use YYYY-MM-DD")
        }
        val category = try {
            ExpenseCategory.valueOf(categoryStr.uppercase())
        } catch (e: IllegalArgumentException) {
            throw ValidationException("Invalid category. Must be BAZAAR or UTILITY")
        }

        return newSuspendedTransaction(Dispatchers.IO) {
            val callerMember = MessMembers.selectAll()
                .where { (MessMembers.messId eq messId) and (MessMembers.userId eq callerUserId) }
                .singleOrNull() ?: throw ForbiddenException("You are not a member of this mess")
            if (callerMember[MessMembers.role] != MemberRole.MANAGER) {
                throw ForbiddenException("Only the manager can add expenses")
            }

            val today = Clock.System.todayIn(TimeZone.UTC)
            if (date > today) throw ValidationException("Cannot log expenses for future dates")

            val cycle = MonthlyCycles.selectAll()
                .where { (MonthlyCycles.messId eq messId) and (MonthlyCycles.status eq CycleStatus.OPEN) }
                .singleOrNull() ?: throw ValidationException("No open cycle found for this mess")
            val cycleId = cycle[MonthlyCycles.id].value

            val result = Expenses.insert {
                it[Expenses.messId] = messId
                it[Expenses.amount] = amount.toBigDecimal()
                it[Expenses.date] = date
                it[Expenses.note] = note
                it[Expenses.receiptPhotoUrl] = receiptPhotoUrl
                it[Expenses.loggedBy] = callerUserId
                it[Expenses.cycleId] = cycleId
                it[Expenses.category] = category
            }

            val callerName = Users.selectAll()
                .where { Users.id eq callerUserId }
                .single()[Users.name]

            ExpenseRecord(
                id = result[Expenses.id].value,
                amount = amount,
                date = dateStr,
                note = note,
                receiptPhotoUrl = receiptPhotoUrl,
                loggedByUserId = callerUserId,
                loggedByName = callerName,
                category = category,
                createdAt = result[Expenses.createdAt].toString()
            )
        }
    }

    suspend fun getExpensesForCurrentCycle(messId: UUID): List<ExpenseRecord> =
        newSuspendedTransaction(Dispatchers.IO) {
            val cycle = MonthlyCycles.selectAll()
                .where { (MonthlyCycles.messId eq messId) and (MonthlyCycles.status eq CycleStatus.OPEN) }
                .singleOrNull() ?: throw ValidationException("No open cycle found for this mess")
            val cycleId = cycle[MonthlyCycles.id].value

            (Expenses innerJoin Users)
                .selectAll()
                .where { (Expenses.messId eq messId) and (Expenses.cycleId eq cycleId) }
                .orderBy(Expenses.date)
                .map { row ->
                    ExpenseRecord(
                        id = row[Expenses.id].value,
                        amount = row[Expenses.amount].toDouble(),
                        date = row[Expenses.date].toString(),
                        note = row[Expenses.note],
                        receiptPhotoUrl = row[Expenses.receiptPhotoUrl],
                        loggedByUserId = row[Expenses.loggedBy].value,
                        loggedByName = row[Users.name],
                        category = row[Expenses.category],
                        createdAt = row[Expenses.createdAt].toString()
                    )
                }
        }

    suspend fun deleteExpense(callerUserId: UUID, messId: UUID, expenseId: UUID) =
        newSuspendedTransaction(Dispatchers.IO) {
            val expense = Expenses.selectAll()
                .where { Expenses.id eq expenseId }
                .singleOrNull() ?: throw NotFoundException("Expense not found")
            if (expense[Expenses.messId].value != messId) throw NotFoundException("Expense not found")

            val callerMember = MessMembers.selectAll()
                .where { (MessMembers.messId eq messId) and (MessMembers.userId eq callerUserId) }
                .singleOrNull() ?: throw ForbiddenException("You are not a member of this mess")
            if (callerMember[MessMembers.role] != MemberRole.MANAGER) {
                throw ForbiddenException("Only the manager can delete expenses")
            }

            Expenses.deleteWhere { with(it) { Expenses.id eq expenseId } }
        }
}
