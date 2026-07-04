package com.prosenjith.messos.db.tables

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object Deposits : UUIDTable("deposits") {
    val messId = reference("mess_id", Messes, onDelete = ReferenceOption.CASCADE)
    val memberId = reference("member_id", MessMembers, onDelete = ReferenceOption.CASCADE)
    val amount = decimal("amount", 10, 2)
    val date = date("date")
    val loggedBy = reference("logged_by", Users)
    val cycleId = reference("cycle_id", MonthlyCycles)
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
}
