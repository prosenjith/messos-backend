package com.prosenjith.messos.services

import com.prosenjith.messos.db.tables.Users
import com.prosenjith.messos.util.DuplicateEntryException
import com.prosenjith.messos.util.PasswordUtils
import com.prosenjith.messos.util.UnauthorizedException
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

data class UserRecord(val id: UUID, val name: String, val phoneOrEmail: String)

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

    suspend fun login(phoneOrEmail: String, password: String): UserRecord =
        newSuspendedTransaction(Dispatchers.IO) {
            val row = Users.selectAll()
                .where { Users.phoneOrEmail eq phoneOrEmail }
                .singleOrNull() ?: throw UnauthorizedException("Invalid credentials")
            if (!PasswordUtils.verify(password, row[Users.passwordHash])) {
                throw UnauthorizedException("Invalid credentials")
            }
            UserRecord(row[Users.id].value, row[Users.name], row[Users.phoneOrEmail])
        }

    suspend fun findById(userId: UUID): UserRecord? =
        newSuspendedTransaction(Dispatchers.IO) {
            Users.selectAll()
                .where { Users.id eq userId }
                .singleOrNull()
                ?.let { UserRecord(it[Users.id].value, it[Users.name], it[Users.phoneOrEmail]) }
        }
}
