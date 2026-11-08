buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9.x ships a built-in Kotlin. Override it with the version this
        // project is designed for. Do not apply org.jetbrains.kotlin.android.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.3.1" apply false
}
