package com.prosenjith.messos.services

import com.prosenjith.messos.db.tables.MemberRole
import com.prosenjith.messos.db.tables.MemberStatus
import com.prosenjith.messos.db.tables.Meals
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
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.upsert
import java.util.UUID

data class MealRecord(
    val id: UUID,
    val memberUserId: UUID,
    val memberName: String,
    val date: String,
    val breakfastCount: Double,
    val lunchCount: Double,
    val dinnerCount: Double,
    val updatedAt: String
)

class MealService {

    suspend fun logMeal(
        callerUserId: UUID,
        messId: UUID,
        targetUserId: UUID,
        dateStr: String,
        breakfastCount: Double,
        lunchCount: Double,
        dinnerCount: Double
    ): MealRecord {
        if (breakfastCount < 0 || lunchCount < 0 || dinnerCount < 0) {
            throw ValidationException("Meal counts cannot be negative")
        }
        val date = try {
            LocalDate.parse(dateStr)
        } catch (e: Exception) {
            throw ValidationException("Invalid date format. Use YYYY-MM-DD")
        }

        return newSuspendedTransaction(Dispatchers.IO) {
            val callerMember = MessMembers.selectAll()
                .where { (MessMembers.messId eq messId) and (MessMembers.userId eq callerUserId) }
                .singleOrNull() ?: throw ForbiddenException("You are not a member of this mess")

            if (callerMember[MessMembers.status] == MemberStatus.LEFT)
                throw ForbiddenException("You have left this mess and cannot make changes")

            if (callerMember[MessMembers.role] == MemberRole.MEMBER && callerUserId != targetUserId) {
                throw ForbiddenException("Members can only log their own meals")
            }

            val (messMemberId, targetName) = requireMemberInMess(messId, targetUserId)

            val targetRow = MessMembers.selectAll().where { MessMembers.id eq messMemberId }.single()
            if (targetRow[MessMembers.status] == MemberStatus.LEFT)
                throw ValidationException("Cannot log meals for a member who has left the mess")
            val cycleStartDate = requireOpenCycle(messId).startDate
            val today = Clock.System.todayIn(TimeZone.UTC)

            if (date < cycleStartDate) throw ValidationException("Date is before the current cycle's start date")
            if (date > today) throw ValidationException("Cannot log meals for future dates")

            Meals.upsert(
                Meals.memberId, Meals.date,
                onUpdateExclude = listOf(Meals.id, Meals.messId, Meals.memberId, Meals.date)
            ) {
                it[Meals.messId] = messId
                it[Meals.memberId] = messMemberId
                it[Meals.date] = date
                it[Meals.breakfastCount] = breakfastCount.toBigDecimal()
                it[Meals.lunchCount] = lunchCount.toBigDecimal()
                it[Meals.dinnerCount] = dinnerCount.toBigDecimal()
                it[Meals.updatedBy] = callerUserId
                it[Meals.updatedAt] = Clock.System.now()
            }

            val mealRow = Meals.selectAll()
                .where { (Meals.memberId eq messMemberId) and (Meals.date eq date) }
                .single()

            MealRecord(
                id = mealRow[Meals.id].value,
                memberUserId = targetUserId,
                memberName = targetName,
                date = mealRow[Meals.date].toString(),
                breakfastCount = mealRow[Meals.breakfastCount].toDouble(),
                lunchCount = mealRow[Meals.lunchCount].toDouble(),
                dinnerCount = mealRow[Meals.dinnerCount].toDouble(),
                updatedAt = mealRow[Meals.updatedAt].toString()
            )
        }
    }

    suspend fun getMealsForCurrentCycle(messId: UUID): List<MealRecord> =
        newSuspendedTransaction(Dispatchers.IO) {
            val cycleStartDate = requireOpenCycle(messId).startDate

            val memberInfo = (MessMembers innerJoin Users)
                .selectAll()
                .where { MessMembers.messId eq messId }
                .associate { it[MessMembers.id].value to (it[Users.id].value to it[Users.name]) }

            Meals.selectAll()
                .where { (Meals.messId eq messId) and (Meals.date greaterEq cycleStartDate) }
                .orderBy(Meals.date)
                .map { row ->
                    val messMemberId = row[Meals.memberId].value
                    val (userId, name) = memberInfo[messMemberId] ?: (messMemberId to "Unknown")
                    MealRecord(
                        id = row[Meals.id].value,
                        memberUserId = userId,
                        memberName = name,
                        date = row[Meals.date].toString(),
                        breakfastCount = row[Meals.breakfastCount].toDouble(),
                        lunchCount = row[Meals.lunchCount].toDouble(),
                        dinnerCount = row[Meals.dinnerCount].toDouble(),
                        updatedAt = row[Meals.updatedAt].toString()
                    )
                }
        }

    suspend fun deleteMeal(callerUserId: UUID, messId: UUID, mealId: UUID) =
        newSuspendedTransaction(Dispatchers.IO) {
            val meal = Meals.selectAll()
                .where { Meals.id eq mealId }
                .singleOrNull() ?: throw NotFoundException("Meal not found")

            if (meal[Meals.messId].value != messId) throw NotFoundException("Meal not found")

            val callerMember = MessMembers.selectAll()
                .where { (MessMembers.messId eq messId) and (MessMembers.userId eq callerUserId) }
                .singleOrNull() ?: throw ForbiddenException("You are not a member of this mess")

            if (callerMember[MessMembers.status] == MemberStatus.LEFT)
                throw ForbiddenException("You have left this mess and cannot make changes")

            if (callerMember[MessMembers.role] != MemberRole.MANAGER &&
                meal[Meals.memberId].value != callerMember[MessMembers.id].value
            ) {
                throw ForbiddenException("You can only delete your own meal entries")
            }

            Meals.deleteWhere { with(it) { Meals.id eq mealId } }
        }
}
