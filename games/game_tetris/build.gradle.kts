// Copy this folder into the platform's games/ directory; no version catalog is required.
plugins {
    id("com.android.library")
}

android {
    namespace = "com.team.tetris"
    compileSdk = 35
    resourcePrefix = "tetris_"

    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint { abortOnError = true }
}

dependencies {
    implementation(project(":game_core"))
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.core:core:1.15.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
