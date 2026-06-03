plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.app.indoor"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation(libs.indooratlas.android.sdk)
    implementation("com.google.maps.android:android-maps-utils:4.3.0")
    implementation("com.squareup.picasso:picasso:2.5.2")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("com.davemorrissey.labs:subsampling-scale-image-view:3.10.0")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")
}