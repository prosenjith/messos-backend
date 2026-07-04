package com.prosenjith.messos.models.auth

import kotlinx.serialization.Serializable

@Serializable
data class SignupRequest(val name: String, val phoneOrEmail: String, val password: String)

@Serializable
data class LoginRequest(val phoneOrEmail: String, val password: String)

@Serializable
data class TokenResponse(val token: String)

@Serializable
data class UserResponse(val id: String, val name: String, val phoneOrEmail: String)
