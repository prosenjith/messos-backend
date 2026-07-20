package com.prosenjith.messos.models.expense

import kotlinx.serialization.Serializable

@Serializable
data class AddExpenseRequest(
    val amount: Double,
    val date: String,
    val note: String? = null,
    val receiptPhotoUrl: String? = null,
    val category: String = "BAZAAR"
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
    val category: String,
    val createdAt: String
)
