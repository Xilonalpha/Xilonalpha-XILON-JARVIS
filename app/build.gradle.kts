plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "com.xilon.jarvis"
 compileSdk = 36
 ndkVersion = "27.2.12479018"
 externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
 defaultConfig {
  externalNativeBuild { cmake { arguments += "-DXILON_VULKAN="+(project.providers.gradleProperty("enableVulkan").orNull == "true").let { if(it) "ON" else "OFF" } } }
 applicationId = "com.xilon.jarvis"; ndk { abiFilters += listOf("arm64-v8a", "x86_64") }; minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "0.1.0" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}

dependencies {
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.camera:camera-core:1.4.2")
 implementation("androidx.camera:camera-camera2:1.4.2")
 implementation("androidx.camera:camera-lifecycle:1.4.2")
 implementation("com.google.mediapipe:tasks-vision:0.10.35")
}

dependencies { implementation("androidx.activity:activity:1.9.3") }

dependencies {
 implementation("com.microsoft.onnxruntime:onnxruntime-android:1.24.3")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

val sherpaEnabled = providers.gradleProperty("enableSherpa").orNull != "false"
if (sherpaEnabled) {
 android.sourceSets.getByName("main").java.srcDir("src/sherpa/java")
 dependencies { implementation("com.github.k2-fsa:sherpa-onnx:v1.12.38") }
}

dependencies { testImplementation("junit:junit:4.13.2") }

dependencies { implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0") }

dependencies { implementation("org.apache.commons:commons-compress:1.27.1") }

dependencies { implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3") }
