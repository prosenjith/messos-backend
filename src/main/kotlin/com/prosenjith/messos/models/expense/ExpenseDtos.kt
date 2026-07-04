package com.prosenjith.messos.models.expense

import kotlinx.serialization.Serializable

@Serializable
data class AddExpenseRequest(
    val amount: Double,
    val date: String,
    val note: String? = null,
    val receiptPhotoUrl: String? = null
)

@Serializable
data class ExpenseResponse(
    val id: String,
    val amount: Double,
    val date: String,
    val note: String?,
    val receiptPhotoUrl: String?,
    val loggedBy: String,
    val loggedByName: String,
    val createdAt: String
)
