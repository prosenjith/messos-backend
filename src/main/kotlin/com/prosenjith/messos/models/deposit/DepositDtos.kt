package com.prosenjith.messos.models.deposit

import kotlinx.serialization.Serializable

@Serializable
data class AddDepositRequest(
    val memberId: String,
    val amount: Double,
    val date: String
)

@Serializable
data class DepositResponse(
    val id: String,
    val memberId: String,
    val memberName: String,
    val amount: Double,
    val date: String,
    val loggedBy: String,
    val loggedByName: String,
    val createdAt: String
)
