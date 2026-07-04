package com.prosenjith.messos.db.tables

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object Messes : UUIDTable("messes") {
    val name = varchar("name", 100)
    val joinCode = varchar("join_code", 8).uniqueIndex()
    val managerId = reference("manager_id", Users)
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
}
