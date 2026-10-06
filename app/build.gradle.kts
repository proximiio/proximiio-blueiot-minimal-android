import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * The app's build-time configuration.
 *
 * The credentials come from the git-ignored root `secrets.properties` (see
 * `secrets.example.properties`). The tracked `venue.properties` next to it sets the
 * default relay-api URL. A value is read from `secrets.properties` first, then from
 * `venue.properties`, then from a Gradle property of the same name; an empty value
 * counts as absent. That order lets CI build with neither file on disk. A value that
 * is empty everywhere means "not configured"; the app still builds and reports it on
 * screen rather than crashing.
 */
val secrets: Properties = properties("secrets.properties")
val venue: Properties = properties("venue.properties")

fun properties(name: String): Properties =
    Properties().apply {
        val file = rootProject.file(name)
        if (file.exists()) file.inputStream().use { load(it) }
    }

fun value(key: String): String =
    sequenceOf(secrets.getProperty(key), venue.getProperty(key), providers.gradleProperty(key).orNull)
        .filterNotNull()
        .map { it.trim().trim('"', '\'').trim() }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()

/** A Kotlin string literal for `buildConfigField`. */
fun quoted(text: String): String =
    "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "io.proximi.blueiot.minimal"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.proximi.blueiot.minimal"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        // Read once by `VenueConfiguration`. None of them is editable at runtime.
        buildConfigField("String", "PROXIMIIO_APPLICATION_TOKEN", quoted(value("PROXIMIIO_APPLICATION_TOKEN")))
        buildConfigField("String", "BLUEIOT_RELAY_URL", quoted(value("BLUEIOT_RELAY_URL")))
        buildConfigField("String", "BLUEIOT_RELAY_APP_TOKEN", quoted(value("BLUEIOT_RELAY_APP_TOKEN")))
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(libs.versions.jvmTarget.get().toInt())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            // Robolectric, for the two stores that use `SharedPreferences`.
            isIncludeAndroidResources = true
        }
    }

    lint {
        // No lint baseline: everything lint reports about this app is fixed.
        abortOnError = true
        warningsAsErrors = true
        checkDependencies = false
        disable +=
            setOf(
                // "A newer version of X is available" is a fact about the day the build
                // ran. As an error it would break CI whenever a dependency is released,
                // and the versions here are pinned; the README quotes them.
                "AndroidGradlePluginVersion",
                "GradleDependency",
                "NewerVersionAvailable",
                // targetSdk is 36 deliberately; see gradle/libs.versions.toml.
                "OldTargetApi",
                // It reports that `res/mipmap-anydpi-v26` could drop the `-v26` at
                // minSdk 26. Without the qualifier the resource merger does not pick the
                // adaptive icon up at all.
                "ObsoleteSdkInt",
            )
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/DEPENDENCIES")
        }
    }
}

dependencies {
    // The SDK, the BlueIoT wristband binding client it positions from, and the venue map.
    implementation(libs.proximiio.sdk)
    implementation(libs.proximiio.sdk.blueiot)
    implementation(libs.proximiio.map)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
