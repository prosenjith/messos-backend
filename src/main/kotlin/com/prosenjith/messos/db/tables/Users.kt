package com.prosenjith.messos.db.tables

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object Users : UUIDTable("users") {
    val name = varchar("name", 100)
    val phoneOrEmail = varchar("phone_or_email", 150).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
}
