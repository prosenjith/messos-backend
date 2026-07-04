package com.prosenjith.messos.config

import io.ktor.server.config.*

data class DatabaseConfig(
    val url: String,
    val driver: String,
    val username: String,
    val password: String,
    val maximumPoolSize: Int,
    val minimumIdle: Int
)

data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val expiryDays: Long
)

data class AppConfig(
    val database: DatabaseConfig,
    val jwt: JwtConfig
) {
    constructor(config: ApplicationConfig) : this(
        database = DatabaseConfig(
            url = config.property("database.url").getString(),
            driver = config.property("database.driver").getString(),
            username = config.property("database.username").getString(),
            password = config.property("database.password").getString(),
            maximumPoolSize = config.property("database.pool.maximumPoolSize").getString().toInt(),
            minimumIdle = config.property("database.pool.minimumIdle").getString().toInt()
        ),
        jwt = JwtConfig(
            secret = config.property("jwt.secret").getString(),
            issuer = config.property("jwt.issuer").getString(),
            audience = config.property("jwt.audience").getString(),
            expiryDays = config.property("jwt.expiryDays").getString().toLong()
        )
    )
}
