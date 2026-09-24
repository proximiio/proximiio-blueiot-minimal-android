import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * The app's build-time configuration.
 *
 * Three credentials come from the git-ignored root `secrets.properties` (see
 * `secrets.example.properties`); one venue value comes from the tracked
 * `venue.properties` next to it. A Gradle property of the same name is the fallback,
 * which is what lets CI build with neither file on disk. An empty value means "not
 * configured"; the app still builds and reports it on screen rather than crashing.
 */
val secrets: Properties = properties("secrets.properties")
val venue: Properties = properties("venue.properties")

fun properties(name: String): Properties =
    Properties().apply {
        val file = rootProject.file(name)
        if (file.exists()) file.inputStream().use { load(it) }
    }

fun value(key: String): String =
    (secrets.getProperty(key) ?: venue.getProperty(key) ?: providers.gradleProperty(key).orNull ?: "")
        .trim()
        .trim('"', '\'')
        .trim()

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
        buildConfigField("String", "BLUEIOT_CLOUD_RELAY_URL", quoted(value("BLUEIOT_CLOUD_RELAY_URL")))
        buildConfigField("String", "BLUEIOT_CLOUD_RELAY_TOKEN", quoted(value("BLUEIOT_CLOUD_RELAY_TOKEN")))
        // The venue's survey value rather than a credential; see venue.properties, which
        // is tracked and carries this venue's working value.
        buildConfigField("String", "BLUEIOT_GROUND_FLOOR_NO", quoted(value("BLUEIOT_GROUND_FLOOR_NO")))
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
    // The SDK, the BlueIoT cloud-relay client it positions from, and the venue map.
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
