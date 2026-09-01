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

/*
 * Only the live courses reach the APK/AAB.
 *
 * `content/_index.json` is the single authority on which courses the app offers; everything not
 * in it is authored-but-hidden and unreachable in the app. Those folders were still being
 * packaged: the v0.65.0 bundle carried French, German, Italian and Spanish, about 12.8 MB of a
 * 21 MB assets folder, for a build that offers Croatian and Portuguese.
 *
 * Driven BY the manifest rather than by a hardcoded list, so the skeleton/content contract holds:
 * re-adding a code to _index.json ships that course again with no edit here. And the unit tests
 * are unaffected - ContentValidationTest reads src/main/assets directly, so a hidden course stays
 * fully validated even while it stays out of the bundle.
 */
val contentDir = file("src/main/assets/content")
val liveLanguages: Set<String> = run {
    val index = File(contentDir, "_index.json")
    if (!index.exists()) emptySet()
    // Any quoted token naming a real course folder. Robust against the manifest being a bare
    // array or an object with a "languages" key, without pulling in a JSON parser.
    else Regex("\"([^\"]+)\"").findAll(index.readText())
        .map { it.groupValues[1] }
        .filter { File(contentDir, it).isDirectory }
        .toSet()
}

val hiddenLanguages: List<String> =
    (contentDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList())
        .filterNot { it in liveLanguages }
        .sorted()

if (liveLanguages.isEmpty()) {
    error(
        "content/_index.json named no course that exists on disk. Refusing to build an app with " +
            "no content rather than shipping an empty assets folder."
    )
}
logger.lifecycle(
    "Corlang assets: shipping ${liveLanguages.sorted()}" +
        if (hiddenLanguages.isEmpty()) "" else ", withholding $hiddenLanguages"
)

val stageLiveAssets = tasks.register<Sync>("stageLiveAssets") {
    description = "Copies assets minus the courses hidden from content/_index.json."
    from("src/main/assets")
    into(layout.buildDirectory.dir("generated/liveAssets"))
    hiddenLanguages.forEach { exclude("content/$it/**") }
}

android {
    namespace = "com.corlang.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.corlang.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 245
        versionName = "0.90.11"
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
            // Debug is now a DEVELOPMENT build only: nothing shipped comes from here. It is
            // signed with the shared ~/.android/debug.keystore, so it can never be a release
            // artefact (see the release buildType below).
            //
            // Opt-in ONLY: `-PtestId` installs debug builds under com.corlang.app.test so a test
            // build sits side-by-side with the real (differently-signed) install without touching
            // it. Normal builds (no flag) keep the shipping applicationId.
            if (project.hasProperty("testId")) applicationIdSuffix = ".test"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // BOTH shipping channels are release builds: the sideload APK
            // (assembleSideloadRelease) and the Play AAB (bundlePlayRelease). A fresh clone with
            // no keystore.properties still CONFIGURES - the guard below is what stops an unsigned
            // artefact being mistaken for a shippable one.
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

    // Ship the courses the app OFFERS, not every course in the repo. See stageLiveAssets below:
    // assets come from a staged copy with the hidden languages left out, so the bundle stops
    // carrying four courses no learner can reach.
    sourceSets.getByName("main") {
        // .map { } on the task provider is what carries the dependency: handing setSrcDirs the
        // task, or the bare path, registers a DIRECTORY and nothing more. stageLiveAssets then
        // never entered the task graph, the asset merge found the directory unchanged, reported
        // UP-TO-DATE, and every APK built between 2026-08-25 and 2026-08-30 shipped a five-day-old
        // copy of the courses while the source tree, all six checkers and 235 tests stayed green.
        // The Play AAB waiting to be uploaded had 10 stale files, the whole Portuguese plan among
        // them. Registry C29; tools/release/check_packaged_content.py is the standing check.
        assets.setSrcDirs(listOf(stageLiveAssets.map { it.destinationDir }))
    }

}


/*
 * A release artefact must be signed by OUR key, or not exist.
 *
 * Until v0.88.0 the sideload channel shipped `assembleSideloadDebug`, so releases/corlang.apk was
 * signed with the shared ~/.android/debug.keystore - `CN=Android Debug`, the key every Android
 * developer on earth holds. It installed fine, so nothing ever complained. But that key names
 * nobody: it cannot be registered for Android developer verification (the Sep 30 2026 deadline
 * for installs on certified devices), and any other app can claim the same signature.
 *
 * Both channels are release builds now. Without keystore.properties a release build is silently
 * UNSIGNED - it configures and compiles, and only fails at install time on the phone. Fail at the
 * task graph instead, so a fresh clone can still build and test everything else.
 */
gradle.taskGraph.whenReady {
    val packagingRelease = allTasks.any {
        it.project == project &&
            Regex("^(package|bundle).*Release").containsMatchIn(it.name)
    }
    if (packagingRelease && !keystorePropsFile.exists()) {
        error(
            "Refusing to build an UNSIGNED release artefact: keystore.properties is missing. " +
                "Copy keystore.properties.example and point it at the release keystore. " +
                "For a build you only want to run locally, use the debug variant " +
                "(:app:assembleSideloadDebug)."
        )
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
