plugins {
    id("com.android.application")
}

android {
    namespace = "com.keepsnotes.moments"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.keepsnotes.moments"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.2"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
