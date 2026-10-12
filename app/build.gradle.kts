plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
// Local CLI builds can keep generated binaries outside synchronised project folders.
providers.environmentVariable("SNAPTOK_BUILD_DIR").orNull?.let { layout.buildDirectory.set(file(it)) }
android {
    namespace = "app.snaptok"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.snaptok"
        minSdk = 29
        targetSdk = 35
        versionCode = 12
        versionName = "0.6.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        // Local testing only: retain the installed preview's signer for safe upgrades.
        // Public releases must use a dedicated release key / Play App Signing.
        create("preview") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs.useLegacyPackaging = true }
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint { abortOnError = true }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
// Variants share versioned Room schemas. Finish one exporter before another reads its file.
tasks.matching { it.name == "kspPreviewKotlin" }.configureEach {
    mustRunAfter(tasks.matching { it.name == "kspDebugKotlin" })
}
tasks.matching { it.name == "kspReleaseKotlin" }.configureEach {
    mustRunAfter(tasks.matching { it.name == "kspDebugKotlin" || it.name == "kspPreviewKotlin" })
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
    implementation("androidx.media3:media3-transformer:1.6.1")
    // Override old transitive versions from the Android extractor wrapper.
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    implementation("commons-io:commons-io:2.18.0")
    androidTestImplementation("androidx.room:room-testing:2.7.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.work:work-testing:2.10.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
