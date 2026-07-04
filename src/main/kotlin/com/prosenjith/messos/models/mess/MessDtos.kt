package com.prosenjith.messos.models.mess

import kotlinx.serialization.Serializable

@Serializable
data class CreateMessRequest(val name: String)

@Serializable
data class JoinMessRequest(val joinCode: String)

@Serializable
data class MessResponse(
    val id: String,
    val name: String,
    val joinCode: String,
    val managerId: String,
    val createdAt: String
)

@Serializable
data class MemberInfo(val id: String, val name: String, val role: String)

@Serializable
data class MessDetailResponse(
    val id: String,
    val name: String,
    val joinCode: String,
    val managerId: String,
    val createdAt: String,
    val members: List<MemberInfo>
)

@Serializable
data class MessWithTokenResponse(val mess: MessResponse, val token: String)
