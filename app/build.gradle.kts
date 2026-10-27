plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.fieldwatch"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "app.fieldwatch"
        minSdk = 29
        targetSdk = 35
        versionCode = 42
        versionName = "1.1.32"
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
