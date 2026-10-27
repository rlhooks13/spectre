plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ADR-0002: the git tag is the version.
// - A clean commit tagged vX.Y.Z, or dryrun/v0.0.Z (the release workflow's scratch run), builds as X.Y.Z.
//   In a tag-triggered CI run the triggering tag wins (GITHUB_REF_NAME), so a revert release that adds a
//   second tag to an old commit builds as the new version. Otherwise the highest valid tag on HEAD wins.
// - Any other build is X.Y.Z-dev+<sha>[.dirty] from the highest valid v* tag HEAD descends from, or 0.0.0.
// versionCode is X·1,000,000 + Y·1,000 + Z. Before the first release, dev builds use 43, one above the
// inherited 1.1.32 build (42), so they still install over it. The release workflow checks the APK against
// the tag, so a wrong answer here fails the release instead of publishing it.
data class AppVersion(val name: String, val code: Int)

val appVersion: AppVersion = run {
    val part = "(0|[1-9][0-9]{0,2})"
    val release = Regex("^v$part\\.$part\\.$part$")
    val dryRun = Regex("^dryrun/v(0)\\.(0)\\.([1-9][0-9]{0,2})$") // 0.0.0 would be versionCode 0
    fun git(vararg args: String): List<String>? = try {
        val result = providers.exec {
            commandLine("git", *args)
            isIgnoreExitValue = true
        }
        if (result.result.get().exitValue != 0) {
            logger.warn("Version: git ${args.joinToString(" ")} failed: ${result.standardError.asText.get().trim()}")
            null
        } else {
            result.standardOutput.asText.get().lines().map { it.trim() }.filter { it.isNotEmpty() }
        }
    } catch (e: Exception) {
        logger.warn("Version: git is not available (${e.message}); building as 0.0.0-dev+unknown.")
        null
    }
    fun parts(m: MatchResult): List<Int> = m.groupValues.drop(1).map { it.toInt() }
    fun code(m: MatchResult): Int = parts(m).let { (x, y, z) -> x * 1_000_000 + y * 1_000 + z }
    fun name(m: MatchResult): String = parts(m).joinToString(".")
    fun tagged(tag: String): MatchResult? = release.matchEntire(tag) ?: dryRun.matchEntire(tag)

    val here = git("tag", "--points-at", "HEAD").orEmpty()
    val dirty = git("status", "--porcelain", "--untracked-files=no")?.isNotEmpty() ?: true
    val trigger = System.getenv("GITHUB_REF_NAME")?.takeIf { System.getenv("GITHUB_REF_TYPE") == "tag" && it in here }
    val exact = (trigger?.let(::tagged) ?: here.mapNotNull(::tagged).maxByOrNull(::code))?.takeIf { !dirty }
    if (exact != null) {
        AppVersion(name(exact), code(exact))
    } else {
        val last = git("tag", "--merged", "HEAD", "--list", "v*").orEmpty().mapNotNull { release.matchEntire(it) }.maxByOrNull(::code)
        val sha = git("rev-parse", "--short=12", "HEAD")?.firstOrNull() ?: "unknown"
        AppVersion("${last?.let(::name) ?: "0.0.0"}-dev+$sha${if (dirty) ".dirty" else ""}", last?.let(::code) ?: 43)
    }
}

android {
    namespace = "app.fieldwatch"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        // Spectre's own id (P0-4e), so it installs next to Fieldwatch. The namespace (package) stays.
        applicationId = "io.github.rlhooks13.spectre"
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
    testOptions {
        // Robolectric tests see the merged manifest and resources.
        unitTests.isIncludeAndroidResources = true
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

// One configuration per SDK: the jars share coordinates, so in one configuration only the newest survives.
val robolectricSdk34: Configuration by configurations.creating { isCanBeConsumed = false }
val robolectricSdk35: Configuration by configurations.creating { isCanBeConsumed = false }

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
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    robolectricSdk34(libs.robolectric.android.all.sdk34) { isTransitive = false }
    robolectricSdk35(libs.robolectric.android.all.sdk35) { isTransitive = false }
}

// Robolectric fetches its Android SDK jar from Maven Central at test time, outside Gradle's dependency
// verification. Instead Gradle resolves (and verifies) the jars it needs, and tests run offline from them.
val robolectricJars = layout.buildDirectory.dir("robolectric-jars")
val syncRobolectricJars by tasks.registering(Sync::class) {
    from(robolectricSdk34, robolectricSdk35)
    into(robolectricJars)
}
tasks.withType<Test>().configureEach {
    // An input, so a new SDK jar reruns the tests; the folder's absolute path stays out of the cache key.
    inputs.files(syncRobolectricJars).withPropertyName("robolectricJars").withPathSensitivity(PathSensitivity.NONE)
    systemProperty("robolectric.offline", "true")
    jvmArgumentProviders += CommandLineArgumentProvider {
        listOf("-Drobolectric.dependency.dir=${robolectricJars.get().asFile.absolutePath}")
    }
}

// Robolectric brings BouncyCastle and Conscrypt onto the test classpath. They are approved there only
// (approved-dependencies.md), so the build fails if either reaches an app runtime classpath.
val checkNoTestCryptoInApp by tasks.registering {
    val forbidden = listOf("org.bouncycastle", "org.conscrypt")
    val classpaths = listOf("debugRuntimeClasspath", "releaseRuntimeClasspath", "stagingRuntimeClasspath")
        .map { name -> name to configurations.named(name).flatMap { it.incoming.resolutionResult.rootComponent } }
    doLast {
        classpaths.forEach { (name, root) ->
            val seen = mutableSetOf<org.gradle.api.artifacts.result.ResolvedComponentResult>()
            fun walk(c: org.gradle.api.artifacts.result.ResolvedComponentResult) {
                if (!seen.add(c)) return
                c.dependencies.filterIsInstance<org.gradle.api.artifacts.result.ResolvedDependencyResult>().forEach { walk(it.selected) }
            }
            walk(root.get())
            val bad = seen.mapNotNull { it.moduleVersion }.filter { it.group in forbidden }
            check(bad.isEmpty()) { "$name contains test-only crypto: ${bad.joinToString()}" }
        }
    }
}
tasks.named("check") { dependsOn(checkNoTestCryptoInApp) }

// Capture scrub tool (P1-F4, docs/capture-runbook.md): `-PscrubIn=<raw dir> -PscrubOut=<corpus dir>`.
// Without them the tool's test is skipped, so CI and ordinary runs never touch capture files. With them,
// the task never counts as up to date: its real input is the raw folder, which Gradle doesn't track.
tasks.matching { it.name == "testDebugUnitTest" }.withType<Test>().configureEach {
    val scrubIn = providers.gradleProperty("scrubIn").getOrElse("")
    systemProperty("spectre.scrubIn", scrubIn)
    systemProperty("spectre.scrubOut", providers.gradleProperty("scrubOut").getOrElse(""))
    systemProperty("spectre.rootDir", rootDir.absolutePath)
    if (scrubIn.isNotBlank()) outputs.upToDateWhen { false }
}
