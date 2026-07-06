package com.prosenjith.messos.plugins

import com.prosenjith.messos.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

fun Application.configureDatabases(config: AppConfig) {
    val hikariConfig = HikariConfig().apply {
        jdbcUrl = config.database.url
        driverClassName = config.database.driver
        username = config.database.username
        password = config.database.password
        maximumPoolSize = config.database.maximumPoolSize
        minimumIdle = config.database.minimumIdle
    }
    val dataSource = HikariDataSource(hikariConfig)

    Flyway.configure(Thread.currentThread().contextClassLoader)
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate()

    Database.connect(dataSource)

    monitor.subscribe(ApplicationStopped) {
        dataSource.close()
    }
}
