plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    lint {
        baseline = file("lint-baseline.xml")
    }

    namespace = "com.sandolpin.weatherquake"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sandolpin.weatherquake"
        // 天気・地震アプリの要件により Android 12 (API 31) 以上をサポート
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.2.5"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// KotlinコンパイラとJavacコンパイラのJVMターゲットが食い違う(例: Kotlinがローカルの新しいJDKを
// 自動検出して21になる等)のを防ぐため、jvmToolchainで両方とも明示的にJDK17に固定する。
// これにより android { compileOptions } と Kotlin側のターゲットが必ず一致するようになる。
kotlin {
    jvmToolchain(17)
}

// 一部の依存関係が推移的に androidx.core を新しすぎるバージョン(compileSdk 37以降を要求するもの)へ
// 引き上げてしまうことがあるため、現在のcompileSdk(35)と噛み合うバージョンに明示的に固定する。
configurations.all {
    resolutionStrategy {
        force("androidx.core:core:1.13.1")
        force("androidx.core:core-ktx:1.13.1")
    }
}

dependencies {
    // --- Compose ---
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // --- AndroidX ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // --- ネットワーク / JSON (既存EEWロジックと共通) ---
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // --- コルーチン ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // --- 位置情報(天気画面の現在地取得) ---
    implementation("com.google.android.gms:play-services-location:21.3.0")
    // MapLibre本体(2026年8月時点の最新版。ビルド時にバージョンが古くなっていたら
// https://mvnrepository.com/artifact/org.maplibre.gl/android-sdk で最新を確認してください)
    implementation("org.maplibre.gl:android-sdk:13.6.0")

// Symbol(震源マーカー)を扱うためのアノテーションプラグイン
    implementation("org.maplibre.gl:android-plugin-annotation-v9:3.0.2")
}