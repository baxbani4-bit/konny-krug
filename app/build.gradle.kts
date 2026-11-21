plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ru.konnykrug.trainer"
    compileSdk = 34

    defaultConfig {
        applicationId = "ru.konnykrug.trainer"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Адрес общего сервера (REST API). Можно переопределить в настройках приложения.
        buildConfigField("String", "DEFAULT_SERVER", "\"http://10.0.2.2:8080\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Чтобы APK можно было установить без Android Studio, подписываем debug-ключом.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    buildFeatures {
        buildConfig = true
        viewBinding = false
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
    // настоящая реализация org.json для локальных тестов (в android.jar только заглушки)
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core:1.6.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
