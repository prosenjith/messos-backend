package com.prosenjith.messos.models

import kotlinx.serialization.Serializable

@Serializable
data class ApiError(val code: String, val message: String)

@Serializable
data class ApiSuccess<T>(val success: Boolean = true, val data: T)

@Serializable
data class ApiFailure(val success: Boolean = false, val error: ApiError)
