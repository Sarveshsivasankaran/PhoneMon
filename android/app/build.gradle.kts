plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Release signing: values come from ~/.gradle/gradle.properties or environment variables
// (PHONEBOX_STORE_FILE, PHONEBOX_STORE_PASSWORD, PHONEBOX_KEY_ALIAS, PHONEBOX_KEY_PASSWORD).
// Without them, release builds fall back to the debug key so the project still builds anywhere.
fun signingValue(name: String): String? =
    (project.findProperty(name) as String?) ?: System.getenv(name)

android {
    namespace = "net.oxog.phonebox.deskpanel"
    compileSdk = 35
    ndkVersion = "28.2.13676358"

    packaging.jniLibs.useLegacyPackaging = true
    packaging.jniLibs.keepDebugSymbols += listOf("**/libjarvis-server.so", "**/libllama*.so", "**/libggml*.so", "**/libmtmd.so")
    sourceSets["main"].jniLibs.srcDir(layout.buildDirectory.dir("phoneRuntime/jni"))

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    defaultConfig {
        applicationId = "net.oxog.phonebox.deskpanel"
        minSdk = 21 // Android 5.0; see CONTRIBUTING.md before changing it
        targetSdk = 35
        versionCode = 9
        versionName = "1.2.2 (stable)"
        buildConfigField("String", "PUBLISHER_NAME", "\"Solo-P-Leveller\"")

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))
        }
    }

    signingConfigs {
        val storeFile = signingValue("PHONEBOX_STORE_FILE")
        if (storeFile != null && file(storeFile).exists()) {
            create("release") {
                this.storeFile = file(storeFile)
                storePassword = signingValue("PHONEBOX_STORE_PASSWORD")
                keyAlias = signingValue("PHONEBOX_KEY_ALIAS")
                keyPassword = signingValue("PHONEBOX_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        // Unsupported framework calls can crash old phones before the panel opens.
        fatal += "NewApi"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.camera:camera-core:1.4.0")
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // android.jar's org.json is a stub on the JVM; the real one lets the unit tests build and read JSON
    testImplementation("org.json:json:20240303")
}

val packageRuntime by tasks.registering(Exec::class) {
    val script = rootProject.file("scripts/package_runtime.py")
    val output = layout.buildDirectory.dir("phoneRuntime/jni/arm64-v8a").get().asFile
    inputs.file(script)
    outputs.dir(output)
    commandLine(if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3", script, output)
}
tasks.named("preBuild") { dependsOn(packageRuntime) }
