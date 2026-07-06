plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(ktorLibs.plugins.ktor)
}

group = "com.prosenjith"
version = "1.0.0-SNAPSHOT"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Core server
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(libs.logback.classic)

    // Ktor features
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.auth.jwt)
    implementation(ktorLibs.server.websockets)
    implementation(ktorLibs.server.callLogging)
    implementation(ktorLibs.server.swagger)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.server.requestValidation)

    // Database
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.flyway.core)

    // Security + Datetime
    implementation(libs.bcrypt)
    implementation(libs.kotlinx.datetime)

    // Test
    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
    testImplementation(libs.mockk)
}
