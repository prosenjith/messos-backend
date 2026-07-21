package com.prosenjith.messos.models.member

import kotlinx.serialization.Serializable

@Serializable
data class ChangeRoleRequest(val role: String)

@Serializable
data class RemoveMemberResponse(val removed: Boolean)

@Serializable
data class ChangeRoleResponse(val memberId: String, val newRole: String)
