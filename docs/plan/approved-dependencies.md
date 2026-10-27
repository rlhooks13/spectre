# Approved dependencies

This file is protected. The agent may add a dependency only if it appears here (same group/artifact; versions are free to change). Anything else needs an owner-reviewed PR to this file first. Gradle dependency verification (`gradle/verification-metadata.xml`, also protected) pins the checksums.

## Already in use
| Group:artifact | Purpose |
|---|---|
| `androidx.compose:compose-bom` and the Compose UI / Material3 / icons-extended artifacts | UI |
| `androidx.activity:activity-compose`, `androidx.core:core-ktx` | Platform |
| `androidx.lifecycle:lifecycle-*` (runtime-ktx, viewmodel-compose, runtime-compose, service) | Lifecycle |
| `androidx.navigation:navigation-compose` | Navigation (type-safe routes) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | Coroutines |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | Serialization |
| `junit:junit`, `org.json:json` (test only) | Tests |

## Approved for the plan
| Group:artifact | Phase | Purpose |
|---|---|---|
| `com.google.devtools.ksp` (plugin) | 2 | Annotation processing |
| `com.google.dagger:hilt-android`, `hilt-compiler`, `hilt-android-testing`; `com.google.dagger.hilt.android` (plugin) | 2 | DI |
| `androidx.hilt:hilt-navigation-compose`, `hilt-work`, `hilt-compiler` | 2–5 | DI integration |
| `androidx.room:room-runtime`, `room-ktx`, `room-compiler`, `room-testing`; `androidx.room` (plugin) | 3 | Database |
| `androidx.datastore:datastore` | 3 | Typed settings |
| `androidx.work:work-runtime-ktx`, `work-testing` | 3–5 | Background jobs |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1 | Tests |
| `app.cash.turbine:turbine` | 1 | Flow tests |
| `org.robolectric:robolectric` | 1 | JVM Android tests |
| `androidx.test:core`, `androidx.test.ext:junit`, `androidx.test:runner`, `androidx.test:rules`, `androidx.test.uiautomator:uiautomator` | 1–5 | Instrumented tests |
| `androidx.compose.ui:ui-test-junit4`, `ui-test-manifest` | 5 | Compose UI tests |
| `io.github.takahirom.roborazzi` (plugin and libraries) | 8 | Screenshots for the manual |
| `io.gitlab.arturbosch.detekt` (plugin) | 0 | Static analysis |

## Not approved
- Network libraries (OkHttp, Ktor, Retrofit). The app keeps one `HttpURLConnection` client behind `NetworkPolicy`.
- Analytics, crash reporting, ads, or any SDK that phones home.
- Crypto libraries (Tink, BouncyCastle). Use `java.security` (SHA256withECDSA) and AndroidKeyStore.
- Anything from a repository other than Google Maven, Maven Central and the Gradle Plugin Portal.
