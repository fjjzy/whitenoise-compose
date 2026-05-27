plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    kotlin("kapt")
}

android {
    namespace = "top.ichiki.whitenoise"
    compileSdk = 34

    defaultConfig {
        applicationId = "top.ichiki.whitenoise"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.5"
    }

    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    kapt {
        javacOptions {
            option("--add-opens", "jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED")
            option("--add-opens", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED")
            option("--add-opens", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED")
            option("--add-opens", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED")
            option("--add-opens", "jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED")
            option("--add-opens", "jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED")
            option("--add-opens", "jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED")
            option("--add-opens", "jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED")
            option("--add-opens", "jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED")
            option("--add-opens", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED")
        }
    }

    // 不压缩 mp3，以便 MediaPlayer 直接从 assets 播放
    aaptOptions {
        noCompress += listOf("mp3")
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.8.2")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Image loading (Coil)
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.media:media:1.7.0")
}