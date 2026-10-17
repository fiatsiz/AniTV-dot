plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "indie.fiatsiz.anitv"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "indie.fiatsiz.anitv"
        minSdk = 34
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    val releaseVariables = listOf(
        "ANITV_RELEASE_KEYSTORE", "ANITV_RELEASE_STORE_PASSWORD",
        "ANITV_RELEASE_KEY_ALIAS", "ANITV_RELEASE_KEY_PASSWORD"
    )
    val releaseCredentials =
        releaseVariables.associateWith { System.getenv(it)?.takeIf(String::isNotBlank) }
    signingConfigs {
        create("ownerRelease") {
            releaseCredentials["ANITV_RELEASE_KEYSTORE"]?.let { storeFile = file(it) }
            storePassword = releaseCredentials["ANITV_RELEASE_STORE_PASSWORD"]
            keyAlias = releaseCredentials["ANITV_RELEASE_KEY_ALIAS"]
            keyPassword = releaseCredentials["ANITV_RELEASE_KEY_PASSWORD"]
        }
    }
    val missingReleaseVariables = releaseVariables.filter { releaseCredentials[it] == null }
    val ownerKeystore = releaseCredentials["ANITV_RELEASE_KEYSTORE"]?.let { file(it) }
    val validateReleaseSigning = tasks.register("validateOwnerReleaseSigning") {
        doLast {
            check(missingReleaseVariables.isEmpty()) { "Release signing requires owner credentials: ${missingReleaseVariables.joinToString()}. See docs/RELEASE.md." }
            check(ownerKeystore?.isFile == true) {
                "ANITV_RELEASE_KEYSTORE must point to the owner's permanent keystore."
            }
        }
    }
    tasks.configureEach {
        if (name == "validateSigningRelease" || name == "packageRelease") dependsOn(
            validateReleaseSigning
        )
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("ownerRelease")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.jsoup)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
