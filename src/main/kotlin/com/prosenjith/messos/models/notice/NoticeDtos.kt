package com.prosenjith.messos.models.notice

import kotlinx.serialization.Serializable

@Serializable
data class PostNoticeRequest(val message: String)

@Serializable
data class NoticeResponse(
    val id: String,
    val message: String,
    val postedBy: String,
    val postedByName: String,
    val createdAt: String
)
