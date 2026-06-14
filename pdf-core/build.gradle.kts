plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.kyant.pdfcore"
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
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.androidx.core.ktx)

    // Lossless PDF page operations (merge / split / reorder / rotate / text extraction).
    // PdfRenderer can only rasterise pages; PdfBox preserves the original text & vectors.
    implementation(libs.pdfbox.android)
}
