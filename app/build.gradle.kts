plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "com.xilon.jarvis"
 compileSdk = 36
 ndkVersion = "27.2.12479018"
 externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
 defaultConfig { applicationId = "com.xilon.jarvis"; ndk { abiFilters += listOf("arm64-v8a", "x86_64") }; minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "0.1.0" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
