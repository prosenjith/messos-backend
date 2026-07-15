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
    val expiryDays: Long,
    val refreshExpiryDays: Long
)

data class StorageConfig(
    val uploadDir: String,
    val baseUrl: String,
    val s3Bucket: String,
    val s3Region: String
)

data class AppConfig(
    val database: DatabaseConfig,
    val jwt: JwtConfig,
    val storage: StorageConfig
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
            expiryDays = config.property("jwt.expiryDays").getString().toLong(),
            refreshExpiryDays = config.property("jwt.refreshExpiryDays").getString().toLong()
        ),
        storage = StorageConfig(
            uploadDir = config.property("storage.uploadDir").getString(),
            baseUrl = config.property("storage.baseUrl").getString(),
            s3Bucket = config.property("storage.s3Bucket").getString(),
            s3Region = config.property("storage.s3Region").getString()
        )
    )
}
