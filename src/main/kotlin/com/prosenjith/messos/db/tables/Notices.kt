package com.prosenjith.messos.db.tables

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object Notices : UUIDTable("notices") {
    val messId = reference("mess_id", Messes, onDelete = ReferenceOption.CASCADE)
    val message = varchar("message", 500)
    val postedBy = reference("posted_by", Users)
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
}
