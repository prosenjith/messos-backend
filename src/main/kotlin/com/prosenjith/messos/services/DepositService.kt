package com.prosenjith.messos.services

import com.prosenjith.messos.db.tables.Deposits
import com.prosenjith.messos.db.tables.MemberRole
import com.prosenjith.messos.db.tables.MessMembers
import com.prosenjith.messos.db.tables.Users
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

data class DepositRecord(
    val id: UUID,
    val memberUserId: UUID,
    val memberName: String,
    val amount: Double,
    val date: String,
    val loggedByUserId: UUID,
    val loggedByName: String,
    val createdAt: String
)

class DepositService {

    suspend fun addDeposit(
        callerUserId: UUID,
        messId: UUID,
        targetUserId: UUID,
        amount: Double,
        dateStr: String
    ): DepositRecord {
        if (amount <= 0) throw ValidationException("Deposit amount must be greater than zero")
        val date = try {
            LocalDate.parse(dateStr)
        } catch (e: Exception) {
            throw ValidationException("Invalid date format. Use YYYY-MM-DD")
        }

        return newSuspendedTransaction(Dispatchers.IO) {
            val callerMember = MessMembers.selectAll()
                .where { (MessMembers.messId eq messId) and (MessMembers.userId eq callerUserId) }
                .singleOrNull() ?: throw ForbiddenException("You are not a member of this mess")
            if (callerMember[MessMembers.role] != MemberRole.MANAGER) {
                throw ForbiddenException("Only the manager can record deposits")
            }

            val today = Clock.System.todayIn(TimeZone.UTC)
            if (date > today) throw ValidationException("Cannot log deposits for future dates")

            val (messMemberId, targetName) = requireMemberInMess(messId, targetUserId)
            val cycleId = requireOpenCycle(messId).id

            val result = Deposits.insert {
                it[Deposits.messId] = messId
                it[Deposits.memberId] = messMemberId
                it[Deposits.amount] = amount.toBigDecimal()
                it[Deposits.date] = date
                it[Deposits.loggedBy] = callerUserId
                it[Deposits.cycleId] = cycleId
            }

            val callerName = Users.selectAll()
                .where { Users.id eq callerUserId }
                .single()[Users.name]

            DepositRecord(
                id = result[Deposits.id].value,
                memberUserId = targetUserId,
                memberName = targetName,
                amount = amount,
                date = dateStr,
                loggedByUserId = callerUserId,
                loggedByName = callerName,
                createdAt = result[Deposits.createdAt].toString()
            )
        }
    }

    suspend fun getDepositsForCurrentCycle(messId: UUID): List<DepositRecord> =
        newSuspendedTransaction(Dispatchers.IO) {
            val cycleId = requireOpenCycle(messId).id

            val memberData = (MessMembers innerJoin Users)
                .selectAll()
                .where { MessMembers.messId eq messId }
                .toList()
            val memberInfoByMemberId = memberData.associate {
                it[MessMembers.id].value to (it[Users.id].value to it[Users.name])
            }
            val nameByUserId = memberData.associate { it[Users.id].value to it[Users.name] }

            Deposits.selectAll()
                .where { (Deposits.messId eq messId) and (Deposits.cycleId eq cycleId) }
                .orderBy(Deposits.date)
                .map { row ->
                    val (memberUserId, memberName) = memberInfoByMemberId[row[Deposits.memberId].value]
                        ?: (row[Deposits.memberId].value to "Unknown")
                    val loggedByName = nameByUserId[row[Deposits.loggedBy].value] ?: "Unknown"
                    DepositRecord(
                        id = row[Deposits.id].value,
                        memberUserId = memberUserId,
                        memberName = memberName,
                        amount = row[Deposits.amount].toDouble(),
                        date = row[Deposits.date].toString(),
                        loggedByUserId = row[Deposits.loggedBy].value,
                        loggedByName = loggedByName,
                        createdAt = row[Deposits.createdAt].toString()
                    )
                }
        }

    suspend fun deleteDeposit(callerUserId: UUID, messId: UUID, depositId: UUID) =
        newSuspendedTransaction(Dispatchers.IO) {
            val deposit = Deposits.selectAll()
                .where { Deposits.id eq depositId }
                .singleOrNull() ?: throw NotFoundException("Deposit not found")
            if (deposit[Deposits.messId].value != messId) throw NotFoundException("Deposit not found")

            val callerMember = MessMembers.selectAll()
                .where { (MessMembers.messId eq messId) and (MessMembers.userId eq callerUserId) }
                .singleOrNull() ?: throw ForbiddenException("You are not a member of this mess")
            if (callerMember[MessMembers.role] != MemberRole.MANAGER) {
                throw ForbiddenException("Only the manager can delete deposits")
            }

            Deposits.deleteWhere { with(it) { Deposits.id eq depositId } }
        }
}
