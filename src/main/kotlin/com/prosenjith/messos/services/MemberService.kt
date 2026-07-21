package com.prosenjith.messos.services

import com.prosenjith.messos.db.tables.MemberRole
import com.prosenjith.messos.db.tables.MessMembers
import com.prosenjith.messos.util.CannotChangeOwnRoleException
import com.prosenjith.messos.util.CannotRemoveSelfException
import com.prosenjith.messos.util.ForbiddenException
import com.prosenjith.messos.util.NotFoundException
import com.prosenjith.messos.util.ValidationException
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

class MemberService {

    suspend fun removeMember(callerUserId: UUID, messId: UUID, targetUserId: UUID) =
        newSuspendedTransaction(Dispatchers.IO) {
            val callerRow = MessMembers.selectAll()
                .where { (MessMembers.messId eq messId) and (MessMembers.userId eq callerUserId) }
                .singleOrNull() ?: throw ForbiddenException("You are not a member of this mess")
            if (callerRow[MessMembers.role] != MemberRole.MANAGER) {
                throw ForbiddenException("Only the manager can remove members")
            }
            if (callerUserId == targetUserId) {
                throw CannotRemoveSelfException("You cannot remove yourself from the mess")
            }
            val deleted = MessMembers.deleteWhere {
                (MessMembers.messId eq messId) and (MessMembers.userId eq targetUserId)
            }
            if (deleted == 0) throw NotFoundException("Member not found in this mess")
        }

    suspend fun changeMemberRole(callerUserId: UUID, messId: UUID, targetUserId: UUID, roleStr: String): MemberRole =
        newSuspendedTransaction(Dispatchers.IO) {
            val callerRow = MessMembers.selectAll()
                .where { (MessMembers.messId eq messId) and (MessMembers.userId eq callerUserId) }
                .singleOrNull() ?: throw ForbiddenException("You are not a member of this mess")
            if (callerRow[MessMembers.role] != MemberRole.MANAGER) {
                throw ForbiddenException("Only the manager can change member roles")
            }
            if (callerUserId == targetUserId) {
                throw CannotChangeOwnRoleException("You cannot change your own role")
            }
            val newRole = try {
                MemberRole.valueOf(roleStr.uppercase())
            } catch (e: IllegalArgumentException) {
                throw ValidationException("Invalid role. Must be MANAGER or MEMBER")
            }
            val updated = MessMembers.update(
                { (MessMembers.messId eq messId) and (MessMembers.userId eq targetUserId) }
            ) {
                it[MessMembers.role] = newRole
            }
            if (updated == 0) throw NotFoundException("Member not found in this mess")
            newRole
        }
}
