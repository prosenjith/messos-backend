package com.prosenjith.messos

import com.prosenjith.messos.config.AppConfig
import com.prosenjith.messos.plugins.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val config = AppConfig(environment.config)
    install(CallLogging)
    configureSerialization()
    configureStatusPages()
    configureSockets()
    configureDatabases(config)
    configureSecurity(config)
    configureRouting(config)
}
