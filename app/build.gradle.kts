plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.mukapp.customland"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.mukapp.customland"
        minSdk = 30
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    buildFeatures {
        viewBinding = true
    }
}

// noinspection GradleDynamicVersion
dependencies {
    // AndroidX / Google
    implementation("androidx.core:core-ktx:_")
    implementation("androidx.appcompat:appcompat:_")
    implementation("com.google.android.material:material:_")
    implementation("androidx.activity:activity:_")
    implementation("androidx.constraintlayout:constraintlayout:_")

    // Test
    testImplementation("junit:junit:_")
    androidTestImplementation("androidx.test.ext:junit:_")
    androidTestImplementation("androidx.test.espresso:espresso-core:_")

    // Third Party
    implementation("com.github.DylanCaiCoding.Longan:longan:_")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:_")
    implementation("com.github.Dimezis:BlurView:_")
}