plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.kyant.ocrcore"
    compileSdk {
        version = release(36)
    }
    buildToolsVersion = "36.1.0"

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging {
        // Tesseract4Android ships its own .so per ABI; nothing to exclude by default,
        // but keep resources.excludes future-proof against META-INF collisions like pdf-core.
        resources {
            excludes += arrayOf("META-INF/LICENSE", "META-INF/LICENSE.md", "META-INF/NOTICE", "META-INF/NOTICE.md")
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    // Bundled (no Play Services / no network) on-device OCR — primary engine.
    // See THIRD_PARTY_NOTICES.md.
    implementation(libs.mlkit.text.recognition)

    // Fully open-source (Apache-2.0) offline OCR fallback for devices where the
    // bundled ML Kit model fails to initialize. See THIRD_PARTY_NOTICES.md.
    implementation(libs.tesseract4android)
}
