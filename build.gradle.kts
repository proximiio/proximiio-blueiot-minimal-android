plugins {
    // Declared here so the app module applies them by alias without repeating versions.
    // NOTE: AGP 9 ships built-in Kotlin support; `org.jetbrains.kotlin.android` is never
    // applied (see gradle/libs.versions.toml).
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
