plugins {
    kotlin("jvm") version "1.4.30"
    kotlin("kapt") version "1.4.30"
    id("com.github.johnrengelman.shadow") version "6.1.0"
}

version = "0.0.1"

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    // Kotlin and coroutines
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.4.2")

    // Discord
    implementation("net.dv8tion:JDA:4.2.0_228")

    // Logging
    implementation("org.slf4j:slf4j-api:1.7.30")
    implementation("org.slf4j:slf4j-jdk14:1.7.30")

    // JSON
    implementation("com.squareup.moshi:moshi:1.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.11.0")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.11.0")

    // Database
    implementation("org.jetbrains.exposed:exposed-core:0.29.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.29.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.29.1")
    implementation("org.jetbrains.exposed:exposed-java-time:0.29.1")
    implementation("org.xerial:sqlite-jdbc:3.30.1")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")

    // Dependency Injection (Koin)
    implementation("org.koin:koin-core:2.2.1")
    implementation("org.koin:koin-core-ext:2.2.1")
    testImplementation("org.koin:koin-test:2.2.1")
}

tasks {
    jar {
        manifest {
            attributes["Main-Class"] = "wisp.app.AppKt"
        }
    }

    shadowJar {
        archiveBaseName.set("wisp")
        archiveClassifier.set("fat")
        archiveVersion.set("")
    }
}