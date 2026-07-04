package com.prosenjith.messos.models.meal

import kotlinx.serialization.Serializable

@Serializable
data class LogMealRequest(
    val memberId: String,
    val date: String,
    val breakfastCount: Double = 0.0,
    val lunchCount: Double = 0.0,
    val dinnerCount: Double = 0.0
)

@Serializable
data class MealResponse(
    val id: String,
    val memberId: String,
    val memberName: String,
    val date: String,
    val breakfastCount: Double,
    val lunchCount: Double,
    val dinnerCount: Double,
    val updatedAt: String
)

@Serializable
data class DeletedResponse(val deleted: Boolean = true)
