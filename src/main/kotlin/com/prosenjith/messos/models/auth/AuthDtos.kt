package com.prosenjith.messos.models.auth

import kotlinx.serialization.Serializable

@Serializable
data class SignupRequest(val name: String, val phoneOrEmail: String, val password: String)

@Serializable
data class LoginRequest(val phoneOrEmail: String, val password: String)

@Serializable
data class TokenResponse(
    val userId: String,
    val token: String,
    val refreshToken: String,
    val messId: String? = null,
    val role: String? = null
)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class UserResponse(val id: String, val name: String, val phoneOrEmail: String)
