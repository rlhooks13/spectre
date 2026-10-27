# Approved dependencies

This file is protected. The agent may add a dependency only if it appears here (same group/artifact; versions are free to change). Anything else needs an owner-reviewed PR to this file first. A listed plugin id also covers its plugin marker and implementation artifacts, so `build-logic` may depend on them to apply that plugin from a convention plugin. Gradle dependency verification (`gradle/verification-metadata.xml`, also protected) pins the checksums.

## Already in use
| Group:artifact | Purpose |
|---|---|
| `com.android.application`, `org.jetbrains.kotlin.android`, `org.jetbrains.kotlin.plugin.compose`, `org.jetbrains.kotlin.plugin.serialization` (plugins) | Build |
| `androidx.compose:compose-bom`; `androidx.compose.ui:ui`, `ui-graphics`, `ui-tooling`, `ui-tooling-preview`; `androidx.compose.material3:material3`; `androidx.compose.material:material-icons-extended` | UI |
| `androidx.activity:activity-compose`, `androidx.core:core-ktx` | Platform |
| `androidx.lifecycle:lifecycle-*` (runtime-ktx, viewmodel-compose, runtime-compose, service) | Lifecycle |
| `androidx.navigation:navigation-compose` | Navigation (type-safe routes) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | Coroutines |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | Serialization |
| `junit:junit`, `org.json:json` (test only) | Tests |

## Approved for the plan
| Group:artifact | Phase | Purpose |
|---|---|---|
| `com.android.library`, `org.jetbrains.kotlin.jvm` (plugins) | 2 | Library modules |
| `com.android.tools.build:gradle`, `org.jetbrains.kotlin:kotlin-gradle-plugin` | 2 | `build-logic` convention plugins (compile-only) |
| `com.google.devtools.ksp` (plugin) | 2 | Annotation processing |
| `com.google.dagger:hilt-android`, `hilt-compiler`, `hilt-android-testing`; `com.google.dagger.hilt.android` (plugin) | 2 | DI |
| `androidx.hilt:hilt-navigation-compose`, `hilt-work`, `hilt-compiler` | 2–5 | DI integration |
| `androidx.room:room-runtime`, `room-ktx`, `room-compiler`, `room-testing`; `androidx.room` (plugin) | 3 | Database |
| `androidx.datastore:datastore` | 3 | Typed settings |
| `androidx.work:work-runtime-ktx`, `work-testing` | 3–5 | Background jobs |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1 | Tests |
| `app.cash.turbine:turbine` | 1 | Flow tests |
| `org.robolectric:robolectric` | 1 | JVM Android tests |
| `org.robolectric:android-all-instrumented` (test only) | 1 | The Android SDK jar Robolectric runs against. Declared so Gradle verifies it and Robolectric runs offline, instead of downloading it unverified at test time |
| Robolectric's transitive dependencies, including `org.bouncycastle:bcprov-jdk18on` and `org.conscrypt:conscrypt-openjdk-uber` (test classpath only) | 1 | Robolectric's shadows of the platform's crypto. `checkNoTestCryptoInApp` fails the build if either group reaches an app runtime classpath |
| `androidx.test:core`, `androidx.test.ext:junit`, `androidx.test:runner`, `androidx.test:rules`, `androidx.test.uiautomator:uiautomator` | 1–5 | Instrumented tests |
| `androidx.compose.ui:ui-test-junit4`, `ui-test-manifest` | 5 | Compose UI tests |
| `io.github.takahirom.roborazzi` (plugin and libraries) | 8 | Screenshots for the manual |
| `io.gitlab.arturbosch.detekt` (plugin) | 0 | Static analysis |

## Not approved
- Network libraries (OkHttp, Ktor, Retrofit). The app keeps one `HttpURLConnection` client behind `NetworkPolicy`.
- Analytics, crash reporting, ads, or any SDK that phones home.
- Crypto libraries (Tink, BouncyCastle). Use `java.security` (SHA256withECDSA) and AndroidKeyStore.
- Anything from a repository other than Google Maven, Maven Central and the Gradle Plugin Portal.
