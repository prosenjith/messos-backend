package com.prosenjith.messos.services

import com.prosenjith.messos.db.tables.CycleStatus
import com.prosenjith.messos.db.tables.MessMembers
import com.prosenjith.messos.db.tables.MonthlyCycles
import com.prosenjith.messos.db.tables.Users
import com.prosenjith.messos.util.NotFoundException
import com.prosenjith.messos.util.ValidationException
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

internal data class MemberInfo(val memberId: UUID, val name: String)

internal fun requireMemberInMess(messId: UUID, userId: UUID): MemberInfo {
    val row = (MessMembers innerJoin Users)
        .selectAll()
        .where { (MessMembers.messId eq messId) and (MessMembers.userId eq userId) }
        .singleOrNull() ?: throw NotFoundException("Member not found in this mess")
    return MemberInfo(row[MessMembers.id].value, row[Users.name])
}

internal data class OpenCycleInfo(val id: UUID, val startDate: LocalDate)

internal fun requireOpenCycle(messId: UUID): OpenCycleInfo {
    val cycle = MonthlyCycles.selectAll()
        .where { (MonthlyCycles.messId eq messId) and (MonthlyCycles.status eq CycleStatus.OPEN) }
        .singleOrNull() ?: throw ValidationException("No open cycle found for this mess")
    return OpenCycleInfo(cycle[MonthlyCycles.id].value, cycle[MonthlyCycles.startDate])
}
