import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// Release signing is configured from a gitignored keystore.properties (never committed — the repo
// is public). If the file is absent, release builds are simply unsigned, so a fresh clone still
// builds. See keystore.properties.example.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

// AI proxy wiring comes from gitignored local.properties (public repo: the worker URL and app
// token must never be committed). Absent keys leave the AI features dark — a fresh clone builds
// an app identical to pre-proxy behavior. See server/ai-proxy/README.md.
val localPropsFile = rootProject.file("local.properties")
val localProps = Properties().apply {
    if (localPropsFile.exists()) localPropsFile.inputStream().use { load(it) }
}
val proxyBaseUrl: String = localProps.getProperty("corlang.proxyBaseUrl") ?: ""
val proxyAuthToken: String = localProps.getProperty("corlang.proxyAuthToken") ?: ""

android {
    namespace = "com.corlang.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.corlang.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 184
        versionName = "0.53.2"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "CORLANG_PROXY_BASE_URL", "\"$proxyBaseUrl\"")
        buildConfigField("String", "CORLANG_PROXY_AUTH_TOKEN", "\"$proxyAuthToken\"")
        // Overridden per-flavor below; the default keeps every build honest.
        buildConfigField("boolean", "DEV_PREMIUM", "false")
    }

    // Two distribution channels that must stay apart: `sideload` (a directly installed APK, the
    // in-app self-updater, and the DEV_PREMIUM test unlock) and `play` (Google Play, which
    // FORBIDS self-updating apps). The play flavor compiles the updater out via ENABLE_UPDATER
    // and merges no REQUEST_INSTALL_PACKAGES / FileProvider — both live in
    // src/sideload/AndroidManifest.xml, which the play flavor never sees.
    flavorDimensions += "distribution"
    productFlavors {
        create("sideload") {
            dimension = "distribution"
            isDefault = true
            // ON for sideload. It was switched off in v0.48.0 on the assumption that Play was
            // the only channel from then on; it is not yet, and a sideload build with no updater
            // is a build that can only be replaced by hand. Play never sees this: the play
            // flavor below keeps it false and merges none of the sideload manifest.
            buildConfigField("boolean", "ENABLE_UPDATER", "true")
            // Pre-billing test unlock (corlang.devPremium=true in local.properties): grants
            // Premium so the AI can be exercised. SIDELOAD ONLY — the play flavor keeps the
            // default false and can never ship a free-Premium build by accident.
            buildConfigField(
                "boolean", "DEV_PREMIUM",
                (localProps.getProperty("corlang.devPremium") == "true").toString()
            )
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "ENABLE_UPDATER", "false")
            // Inherits CORLANG_PROXY_* from defaultConfig: Play subscribers reach the AI proxy.
            // The shared app token in the binary is bounded by the worker's per-IP/global daily
            // rate limits + the per-subscriber 40/day cap keyed on the Play sub token; full
            // Play-Developer-API sub-token verification is the pre-production hardening step.
        }
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Opt-in ONLY: `-PtestId` installs debug builds under com.corlang.app.test so a test
            // build sits side-by-side with the real (differently-signed) install without touching
            // it. Normal builds (no flag) keep the shipping applicationId, so the sideload-debug
            // release channel is unaffected.
            if (project.hasProperty("testId")) applicationIdSuffix = ".test"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true   // for the per-flavor ENABLE_UPDATER switch
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

// Room writes one JSON per schema version here; the directory is COMMITTED so migrations can
// be regression-tested and a released schema is never lost (post-launch a botched migration
// is unrecoverable).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("androidx.work:work-runtime-ktx:2.9.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Google Play Billing — subscriptions (AI Premium) + one-time level unlocks.
    // Play Billing 8, pinned at 8.0.0 deliberately.
    //
    // Google stops accepting v7 for new apps and updates on 2026-08-31, so 7.1.1 would have
    // shipped an app that could not be updated nine days later -- mid closed-test. v8 is
    // accepted until 2027-08-31.
    //
    // Not 8.2.1+ or 9.x: their metadata is built with Kotlin 2.2/2.3 and this project is on
    // 2.0, so they fail to compile with "incompatible version of Kotlin". Moving up means a
    // toolchain upgrade (Kotlin + the Compose compiler together), which is its own change and
    // not one to make on the way to a store upload. Revisit when the toolchain moves; the
    // deadline for that is 2027-08-31.
    implementation("com.android.billingclient:billing-ktx:8.0.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}

/*
 * ContentValidationTest is the content quality gate, and it reads the REAL asset JSON straight
 * off disk by relative path rather than through the test classpath. Gradle therefore could not
 * see the content as an input: editing a lesson and re-running the build reported BUILD
 * SUCCESSFUL in two seconds without executing a single assertion, because nothing on the
 * declared inputs had changed. A green build that never ran the gate is worse than a red one.
 *
 * Declaring the content directory as an input makes any lesson edit re-run the gate.
 */
tasks.withType<Test>().configureEach {
    inputs.dir("src/main/assets/content")
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("courseContent")
}
