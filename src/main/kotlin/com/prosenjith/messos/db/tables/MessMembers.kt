package com.prosenjith.messos.db.tables

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

enum class MemberRole { MANAGER, MEMBER }

object MessMembers : UUIDTable("mess_members") {
    val messId = reference("mess_id", Messes, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val role = enumerationByName("role", 10, MemberRole::class)
    val joinedAt = timestamp("joined_at").clientDefault { Clock.System.now() }

    init {
        uniqueIndex(messId, userId)
    }
}
