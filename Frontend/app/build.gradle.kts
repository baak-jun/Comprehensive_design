plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.counseling"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.counseling"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.4.9"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf(
                    "-std=c++17",
                    "-O3",
                    "-fexceptions",
                    "-frtti",
                    "-march=armv8.7-a"
                )
                arguments += listOf(
                    "-DANDROID_PLATFORM=android-28",
                    "-DGGML_OPENMP=OFF",
                    "-DGGML_LLAMAFILE=OFF"
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    androidResources {
        noCompress += listOf("gguf", "litertlm", "task")
        ignoreAssetsPattern = "*.gguf"
    }
}

tasks.register("copyDatedDebugApk") {
    doLast {
        copy {
            from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
            into(layout.buildDirectory.dir("outputs/apk/debug"))
            rename { "Counseling_05_29_v0.4.9_debug.apk" }
        }
    }
}

afterEvaluate {
    tasks.named("assembleDebug") {
        finalizedBy("copyDatedDebugApk")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.health.connect)
    implementation(libs.litertlm.android)
    implementation(libs.mediapipe.tasks.genai)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
