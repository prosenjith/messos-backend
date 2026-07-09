package com.prosenjith.messos.services

import com.prosenjith.messos.config.JwtConfig
import com.prosenjith.messos.db.tables.MemberRole
import com.prosenjith.messos.db.tables.MessMembers
import com.prosenjith.messos.db.tables.Messes
import com.prosenjith.messos.db.tables.MonthlyCycles
import com.prosenjith.messos.db.tables.Users
import com.prosenjith.messos.util.ForbiddenException
import com.prosenjith.messos.util.InvalidJoinCodeException
import com.prosenjith.messos.util.JwtUtils
import com.prosenjith.messos.util.ValidationException
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

data class MessRecord(
    val id: UUID,
    val name: String,
    val joinCode: String,
    val managerId: UUID,
    val createdAt: String
)

data class MemberRecord(val id: UUID, val name: String, val role: String)
data class MessWithToken(val mess: MessRecord, val token: String)
data class MessDetail(val mess: MessRecord, val members: List<MemberRecord>)

class MessService {

    suspend fun createMess(userId: UUID, messName: String, jwtConfig: JwtConfig): MessWithToken {
        if (messName.isBlank()) throw ValidationException("Mess name cannot be blank")
        return newSuspendedTransaction(Dispatchers.IO) {
            if (MessMembers.selectAll().where { MessMembers.userId eq userId }.any()) {
                throw ValidationException("You are already a member of a mess")
            }
            val joinCode = generateJoinCode()
            val messResult = Messes.insert {
                it[Messes.name] = messName.trim()
                it[Messes.joinCode] = joinCode
                it[Messes.managerId] = userId
            }
            val messId = messResult[Messes.id].value

            MessMembers.insert {
                it[MessMembers.messId] = messId
                it[MessMembers.userId] = userId
                it[MessMembers.role] = MemberRole.MANAGER
            }

            MonthlyCycles.insert {
                it[MonthlyCycles.messId] = messId
                it[MonthlyCycles.startDate] = Clock.System.todayIn(TimeZone.UTC)
            }

            val token = JwtUtils.generateToken(jwtConfig, userId, messId, "MANAGER")
            MessWithToken(
                mess = MessRecord(messId, messName.trim(), joinCode, userId, messResult[Messes.createdAt].toString()),
                token = token
            )
        }
    }

    suspend fun joinMess(userId: UUID, joinCode: String, jwtConfig: JwtConfig): MessWithToken =
        newSuspendedTransaction(Dispatchers.IO) {
            val messRow = Messes.selectAll()
                .where { Messes.joinCode eq joinCode.uppercase() }
                .singleOrNull() ?: throw InvalidJoinCodeException("Invalid join code")

            if (MessMembers.selectAll().where { MessMembers.userId eq userId }.any()) {
                throw ValidationException("You are already a member of a mess")
            }

            val messId = messRow[Messes.id].value
            MessMembers.insert {
                it[MessMembers.messId] = messId
                it[MessMembers.userId] = userId
                it[MessMembers.role] = MemberRole.MEMBER
            }

            val token = JwtUtils.generateToken(jwtConfig, userId, messId, "MEMBER")
            MessWithToken(
                mess = MessRecord(
                    id = messId,
                    name = messRow[Messes.name],
                    joinCode = messRow[Messes.joinCode],
                    managerId = messRow[Messes.managerId].value,
                    createdAt = messRow[Messes.createdAt].toString()
                ),
                token = token
            )
        }

    suspend fun getMessById(messId: UUID, requestingUserId: UUID): MessDetail =
        newSuspendedTransaction(Dispatchers.IO) {
            MessMembers.selectAll()
                .where { (MessMembers.messId eq messId) and (MessMembers.userId eq requestingUserId) }
                .singleOrNull() ?: throw ForbiddenException("You are not a member of this mess")

            val messRow = Messes.selectAll()
                .where { Messes.id eq messId }
                .single()

            val members = (MessMembers innerJoin Users)
                .selectAll()
                .where { MessMembers.messId eq messId }
                .map { row ->
                    MemberRecord(
                        id = row[Users.id].value,
                        name = row[Users.name],
                        role = row[MessMembers.role].name
                    )
                }

            MessDetail(
                mess = MessRecord(
                    id = messId,
                    name = messRow[Messes.name],
                    joinCode = messRow[Messes.joinCode],
                    managerId = messRow[Messes.managerId].value,
                    createdAt = messRow[Messes.createdAt].toString()
                ),
                members = members
            )
        }

    private fun generateJoinCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..5).map { chars.random() }.joinToString("")
    }
}
