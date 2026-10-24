plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

/*
 * Frozen snapshot of the old app's data layer (Kotlin/Android).
 *
 * Deliberately no Compose, no Hilt/KSP, no UI modules: only Android + HTTP/parsing/storage.
 * The same package names as the old modules are kept so call sites can be lifted verbatim;
 * the hosting app is expected to map these models to its own.
 */
android {
    namespace = "com.ahu.ahutong.legacy"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        // com/ahu/ahutong/BuildConfig.kt is a hand-written constant object instead.
        buildConfig = false
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/LICENSE.md",
            "META-INF/LICENSE-notice.md"
        )
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.gson)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.jsoup)

    implementation(libs.mmkv.static)
    implementation(libs.persistentcookiejar)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.commonmark.tables)
    implementation(libs.commonmark.strikethrough)
    implementation(libs.coil)

    // The snapshot only carries javax.inject annotations; no Hilt/Dagger code generation or
    // runtime is involved, so the jar stays compile-only.
    compileOnly(libs.javax.inject)
}
