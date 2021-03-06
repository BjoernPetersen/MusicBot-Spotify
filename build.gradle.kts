import com.diffplug.spotless.LineEnding
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.diffplug.gradle.spotless") version Plugin.SPOTLESS

    id("com.github.ben-manes.versions") version Plugin.VERSIONS
    kotlin("jvm") version Plugin.KOTLIN
    idea

    id("com.github.johnrengelman.shadow") version Plugin.SHADOW_JAR
}

group = "com.github.bjoernpetersen"
version = "0.19.0"

repositories {
    jcenter()
    maven("https://oss.sonatype.org/content/repositories/snapshots") {
        mavenContent {
            snapshotsOnly()
        }
    }
}

idea {
    module {
        isDownloadJavadoc = true
    }
}

spotless {
    kotlin {
        ktlint()
        lineEndings = LineEnding.UNIX
        endWithNewline()
    }
    kotlinGradle {
        ktlint()
        lineEndings = LineEnding.UNIX
        endWithNewline()
    }
    format("markdown") {
        target("**/*.md")
        lineEndings = LineEnding.UNIX
        endWithNewline()
    }
}

tasks {
    "compileKotlin"(KotlinCompile::class) {
        kotlinOptions.jvmTarget = "1.8"
    }

    "compileTestKotlin"(KotlinCompile::class) {
        kotlinOptions.jvmTarget = "1.8"
    }

    "test"(Test::class) {
        useJUnitPlatform()
        systemProperties["junit.jupiter.execution.parallel.enabled"] = true
    }
}

configurations.all {
    resolutionStrategy.cacheChangingModulesFor(1, TimeUnit.MINUTES)
}

dependencies {
    compileOnly(
        group = "com.github.bjoernpetersen",
        name = "musicbot",
        version = Lib.MUSICBOT
    ) {
        isChanging = Lib.MUSICBOT.contains("SNAPSHOT")
    }

    implementation(
        group = "se.michaelthelin.spotify",
        name = "spotify-web-api-java",
        version = Lib.SPOTIFY
    ) {
        exclude("org.slf4j")
        exclude("com.google.guava")
        exclude("com.google.inject")
    }
    implementation(
        group = "io.ktor",
        name = "ktor-client-cio",
        version = Lib.KTOR
    ) {
        exclude("org.slf4j")
        exclude("org.jetbrains")
        exclude("org.jetbrains.kotlin")
        exclude("com.google.guava")
        exclude("com.google.inject")
        exclude("org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    }

    // Ktor for OAuth callback
    implementation(
        group = "io.ktor",
        name = "ktor-server-cio",
        version = Lib.KTOR
    ) {
        exclude("org.slf4j")
        exclude("org.jetbrains")
        exclude("org.jetbrains.kotlin")
        exclude("com.google.guava")
        exclude("com.google.inject")
        exclude("org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    }

    testImplementation(
        group = "org.slf4j",
        name = "slf4j-simple",
        version = Lib.SLF4J
    )
    testImplementation(
        group = "com.github.bjoernpetersen",
        name = "musicbot",
        version = Lib.MUSICBOT
    )
    testImplementation(
        group = "org.junit.jupiter",
        name = "junit-jupiter-api",
        version = Lib.JUNIT
    )
    testRuntimeOnly(
        group = "org.junit.jupiter",
        name = "junit-jupiter-engine",
        version = Lib.JUNIT
    )
}
