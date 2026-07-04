package com.prosenjith.messos.services

import com.prosenjith.messos.db.tables.MemberRole
import com.prosenjith.messos.db.tables.MessMembers
import com.prosenjith.messos.db.tables.Notices
import com.prosenjith.messos.db.tables.Users
import com.prosenjith.messos.util.ForbiddenException
import com.prosenjith.messos.util.NotFoundException
import com.prosenjith.messos.util.ValidationException
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

data class NoticeRecord(
    val id: UUID,
    val message: String,
    val postedByUserId: UUID,
    val postedByName: String,
    val createdAt: String
)

class NoticeService {

    suspend fun postNotice(callerUserId: UUID, messId: UUID, message: String): NoticeRecord {
        if (message.isBlank()) throw ValidationException("Notice message cannot be blank")

        return newSuspendedTransaction(Dispatchers.IO) {
            val callerMember = MessMembers.selectAll()
                .where { (MessMembers.messId eq messId) and (MessMembers.userId eq callerUserId) }
                .singleOrNull() ?: throw ForbiddenException("You are not a member of this mess")
            if (callerMember[MessMembers.role] != MemberRole.MANAGER) {
                throw ForbiddenException("Only the manager can post notices")
            }

            val result = Notices.insert {
                it[Notices.messId]    = messId
                it[Notices.message]   = message
                it[Notices.postedBy]  = callerUserId
            }

            val callerName = Users.selectAll()
                .where { Users.id eq callerUserId }
                .single()[Users.name]

            NoticeRecord(
                id             = result[Notices.id].value,
                message        = message,
                postedByUserId = callerUserId,
                postedByName   = callerName,
                createdAt      = result[Notices.createdAt].toString()
            )
        }
    }

    suspend fun getNotices(messId: UUID): List<NoticeRecord> =
        newSuspendedTransaction(Dispatchers.IO) {
            (Notices innerJoin Users)
                .selectAll()
                .where { Notices.messId eq messId }
                .orderBy(Notices.createdAt, SortOrder.DESC)
                .map { row ->
                    NoticeRecord(
                        id             = row[Notices.id].value,
                        message        = row[Notices.message],
                        postedByUserId = row[Notices.postedBy].value,
                        postedByName   = row[Users.name],
                        createdAt      = row[Notices.createdAt].toString()
                    )
                }
        }

    suspend fun deleteNotice(callerUserId: UUID, messId: UUID, noticeId: UUID) =
        newSuspendedTransaction(Dispatchers.IO) {
            val notice = Notices.selectAll()
                .where { Notices.id eq noticeId }
                .singleOrNull() ?: throw NotFoundException("Notice not found")
            if (notice[Notices.messId].value != messId) throw NotFoundException("Notice not found")

            val callerMember = MessMembers.selectAll()
                .where { (MessMembers.messId eq messId) and (MessMembers.userId eq callerUserId) }
                .singleOrNull() ?: throw ForbiddenException("You are not a member of this mess")
            if (callerMember[MessMembers.role] != MemberRole.MANAGER) {
                throw ForbiddenException("Only the manager can delete notices")
            }

            Notices.deleteWhere { with(it) { Notices.id eq noticeId } }
        }
}
