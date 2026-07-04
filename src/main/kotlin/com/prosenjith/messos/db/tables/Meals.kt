package com.prosenjith.messos.db.tables

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.math.BigDecimal

object Meals : UUIDTable("meals") {
    val messId = reference("mess_id", Messes, onDelete = ReferenceOption.CASCADE)
    val memberId = reference("member_id", MessMembers, onDelete = ReferenceOption.CASCADE)
    val date = date("date")
    val breakfastCount = decimal("breakfast_count", 3, 1).default(BigDecimal.ZERO)
    val lunchCount = decimal("lunch_count", 3, 1).default(BigDecimal.ZERO)
    val dinnerCount = decimal("dinner_count", 3, 1).default(BigDecimal.ZERO)
    val updatedBy = reference("updated_by", Users)
    val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }

    init {
        uniqueIndex(memberId, date)
    }
}
