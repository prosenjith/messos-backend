package com.prosenjith.messos.services

import com.prosenjith.messos.config.JwtConfig
import com.prosenjith.messos.db.tables.MessMembers
import com.prosenjith.messos.db.tables.RefreshTokens
import com.prosenjith.messos.db.tables.Users
import com.prosenjith.messos.util.DuplicateEntryException
import com.prosenjith.messos.util.PasswordUtils
import com.prosenjith.messos.util.UnauthorizedException
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.util.UUID
import kotlin.time.Duration.Companion.days

data class UserRecord(val id: UUID, val name: String, val phoneOrEmail: String, val profileImageUrl: String? = null)

data class LoginResult(
    val userId: UUID,
    val messId: UUID?,
    val role: String?
)

class AuthService {

    suspend fun signup(name: String, phoneOrEmail: String, password: String): UserRecord =
        newSuspendedTransaction(Dispatchers.IO) {
            if (Users.selectAll().where { Users.phoneOrEmail eq phoneOrEmail }.any()) {
                throw DuplicateEntryException("User with this phone/email already exists")
            }
            val hash = PasswordUtils.hash(password)
            val result = Users.insert {
                it[Users.name] = name
                it[Users.phoneOrEmail] = phoneOrEmail
                it[Users.passwordHash] = hash
            }
            val insertedId = result[Users.id].value
            UserRecord(insertedId, name, phoneOrEmail)
        }

    suspend fun login(phoneOrEmail: String, password: String): LoginResult =
        newSuspendedTransaction(Dispatchers.IO) {
            val row = Users.selectAll()
                .where { Users.phoneOrEmail eq phoneOrEmail }
                .singleOrNull() ?: throw UnauthorizedException("Invalid credentials")
            if (!PasswordUtils.verify(password, row[Users.passwordHash])) {
                throw UnauthorizedException("Invalid credentials")
            }
            val userId = row[Users.id].value
            val memberRow = MessMembers.selectAll()
                .where { MessMembers.userId eq userId }
                .singleOrNull()
            LoginResult(
                userId = userId,
                messId = memberRow?.get(MessMembers.messId)?.value,
                role = memberRow?.get(MessMembers.role)?.name
            )
        }

    suspend fun findById(userId: UUID): UserRecord? =
        newSuspendedTransaction(Dispatchers.IO) {
            Users.selectAll()
                .where { Users.id eq userId }
                .singleOrNull()
                ?.let { UserRecord(it[Users.id].value, it[Users.name], it[Users.phoneOrEmail], it[Users.profileImageUrl]) }
        }

    suspend fun updateProfileImage(userId: UUID, url: String): UserRecord =
        newSuspendedTransaction(Dispatchers.IO) {
            Users.update({ Users.id eq userId }) {
                it[profileImageUrl] = url
            }
            Users.selectAll()
                .where { Users.id eq userId }
                .single()
                .let { UserRecord(it[Users.id].value, it[Users.name], it[Users.phoneOrEmail], it[Users.profileImageUrl]) }
        }

    suspend fun issueRefreshToken(userId: UUID, config: JwtConfig): String =
        newSuspendedTransaction(Dispatchers.IO) {
            val token = UUID.randomUUID().toString()
            val expiresAt = Clock.System.now().plus(config.refreshExpiryDays.days)
            RefreshTokens.insert {
                it[RefreshTokens.userId] = userId
                it[RefreshTokens.token] = token
                it[RefreshTokens.expiresAt] = expiresAt
            }
            token
        }

    suspend fun rotateRefreshToken(oldToken: String, config: JwtConfig): Pair<String, LoginResult> =
        newSuspendedTransaction(Dispatchers.IO) {
            val row = RefreshTokens.selectAll()
                .where { RefreshTokens.token eq oldToken }
                .singleOrNull() ?: throw UnauthorizedException("Invalid refresh token")

            if (row[RefreshTokens.expiresAt] < Clock.System.now()) {
                RefreshTokens.deleteWhere { with(it) { RefreshTokens.token eq oldToken } }
                throw UnauthorizedException("Refresh token expired")
            }

            val userId = row[RefreshTokens.userId].value
            RefreshTokens.deleteWhere { with(it) { RefreshTokens.token eq oldToken } }

            val newToken = UUID.randomUUID().toString()
            val expiresAt = Clock.System.now().plus(config.refreshExpiryDays.days)
            RefreshTokens.insert {
                it[RefreshTokens.userId] = userId
                it[RefreshTokens.token] = newToken
                it[RefreshTokens.expiresAt] = expiresAt
            }

            val memberRow = MessMembers.selectAll()
                .where { MessMembers.userId eq userId }
                .singleOrNull()

            newToken to LoginResult(
                userId = userId,
                messId = memberRow?.get(MessMembers.messId)?.value,
                role = memberRow?.get(MessMembers.role)?.name
            )
        }

    suspend fun revokeRefreshToken(token: String) =
        newSuspendedTransaction(Dispatchers.IO) {
            RefreshTokens.deleteWhere { with(it) { RefreshTokens.token eq token } }
        }
}
