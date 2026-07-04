package com.prosenjith.messos.util

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.prosenjith.messos.config.JwtConfig
import java.util.Date
import java.util.UUID

object JwtUtils {
    fun generateToken(
        config: JwtConfig,
        userId: UUID,
        messId: UUID? = null,
        role: String? = null
    ): String {
        val expiryMs = config.expiryDays * 24 * 60 * 60 * 1000L
        return JWT.create()
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withSubject(userId.toString())
            .apply {
                messId?.let { withClaim("messId", it.toString()) }
                role?.let { withClaim("role", it) }
            }
            .withExpiresAt(Date(System.currentTimeMillis() + expiryMs))
            .sign(Algorithm.HMAC256(config.secret))
    }
}
