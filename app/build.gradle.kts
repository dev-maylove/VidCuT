plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseKeystoreFile = providers.gradleProperty("KEYSTORE_FILE").orNull
val releaseKeystorePassword = providers.gradleProperty("KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.gradleProperty("KEY_ALIAS").orNull
val releaseKeyPassword = providers.gradleProperty("KEY_PASSWORD").orNull

android {
    namespace = "id.jsn.vidcut"
    compileSdk = 37

    defaultConfig {
        applicationId = "id.jsn.vidcut"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "1.11.0"
    }

    signingConfigs {
        create("release") {
            if (releaseKeystoreFile != null &&
                releaseKeystorePassword != null &&
                releaseKeyAlias != null &&
                releaseKeyPassword != null
            ) {
                storeFile = file(releaseKeystoreFile)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)

    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.work:work-runtime-ktx:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.media3:media3-transformer:1.11.1")
    implementation("androidx.media3:media3-effect:1.11.1")
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.ai.edge.litert:litert:2.1.5")
}
