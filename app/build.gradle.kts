plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ADR-0002: the git tag is the version. A commit tagged vX.Y.Z (or dryrun/v0.0.Z, the release workflow's
// scratch run) builds as X.Y.Z; any other commit builds as X.Y.Z-dev+<sha> from the last v* tag it descends
// from, or 0.0.0 before the first release. versionCode is X·1,000,000 + Y·1,000 + Z. The release workflow
// checks the APK against the tag, so this can't publish a wrong version.
data class AppVersion(val name: String, val code: Int)

val appVersion: AppVersion = run {
    val part = "(0|[1-9][0-9]{0,2})"
    val release = Regex("^v$part\\.$part\\.$part$")
    val dryRun = Regex("^dryrun/v(0)\\.(0)\\.$part$")
    fun git(vararg args: String): List<String> {
        val result = providers.exec {
            commandLine("git", *args)
            isIgnoreExitValue = true
        }
        if (result.result.get().exitValue != 0) return emptyList()
        return result.standardOutput.asText.get().lines().map { it.trim() }.filter { it.isNotEmpty() }
    }
    fun code(m: MatchResult): Int = m.groupValues.drop(1).map { it.toInt() }.let { (x, y, z) -> x * 1_000_000 + y * 1_000 + z }
    fun name(m: MatchResult): String = m.groupValues.drop(1).joinToString(".")

    val here = git("tag", "--points-at", "HEAD")
    val exact = here.firstNotNullOfOrNull { release.matchEntire(it) } ?: here.firstNotNullOfOrNull { dryRun.matchEntire(it) }
    if (exact != null) {
        AppVersion(name(exact), code(exact))
    } else {
        val last = git("describe", "--tags", "--abbrev=0", "--match", "v[0-9]*").firstOrNull()?.let { release.matchEntire(it) }
        val sha = git("rev-parse", "--short=12", "HEAD").firstOrNull() ?: "unknown"
        AppVersion("${last?.let(::name) ?: "0.0.0"}-dev+$sha", maxOf(1, last?.let(::code) ?: 0))
    }
}

android {
    namespace = "app.fieldwatch"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "app.fieldwatch"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersion.code
        versionName = appVersion.name
        vectorDrawables.useSupportLibrary = true
    }

    // Release builds are never signed here: the CI sign job signs them.
    // Staging uses the committed, non-secret test keystore once the owner adds it (PLAN: Build
    // types); until then it falls back to the per-machine debug key.
    val stagingKeystore = file("signing/staging-test.jks")
    signingConfigs {
        if (stagingKeystore.isFile) {
            create("stagingTest") {
                storeFile = stagingKeystore
                storePassword = "spectre-staging"
                keyAlias = "staging"
                keyPassword = "spectre-staging"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".staging"
            signingConfig = signingConfigs.findByName("stagingTest") ?: signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

androidComponents {
    // Staging shares release's code; running its unit tests again would only inflate the test count.
    beforeVariants(selector().withBuildType("staging")) { it.enableUnitTest = false }
    finalizeDsl { android ->
        check(android.buildTypes.getByName("release").signingConfig == null) {
            "Release must stay unsigned in Gradle; the CI sign job signs it."
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    // Real org.json on the JVM test classpath; the android.jar bundled JSONObject is a stub.
    testImplementation(libs.json)
}
