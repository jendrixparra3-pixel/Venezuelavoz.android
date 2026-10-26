plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.venezuela.voz"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.venezuela.voz"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Servidor WebSocket local: Minecraft se conecta a él con /connect
    implementation("org.java-websocket:Java-WebSocket:1.5.7")
}
