package com.prosenjith.messos.db.tables

import com.prosenjith.messos.util.ExpenseCategory
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object Expenses : UUIDTable("expenses") {
    val messId = reference("mess_id", Messes, onDelete = ReferenceOption.CASCADE)
    val amount = decimal("amount", 10, 2)
    val date = date("date")
    val note = varchar("note", 200).nullable()
    val receiptPhotoUrl = varchar("receipt_photo_url", 500).nullable()
    val loggedBy = reference("logged_by", Users)
    val cycleId = reference("cycle_id", MonthlyCycles)
    val category = enumerationByName<ExpenseCategory>("category", 10).default(ExpenseCategory.BAZAAR)
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
}
