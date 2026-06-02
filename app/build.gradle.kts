plugins {
    //alias(libs.plugins.android.application)
    id("com.android.library")
    id("maven-publish")
}
/*s.abhinav*/
/*val apiKey: String = project.findProperty("indoorAtlasApiKey") as String?
    ?: "0060e83b-5b53-4c80-bd53-6d4b10629cc0"*/
val apiKey: String = project.findProperty("indoorAtlasApiKey") as String?
    ?: "e27f6c17-2b41-4cd0-ba1f-bdabadb0950f"

/*s.abhinav*/
/*val apiSecret: String = project.findProperty("indoorAtlasApiSecret") as String?
    ?: "xFlAzInbxXmUsESTknIA0WdU7RfXwdsjD5rYHNd3sGOK2dOBS3nHuC8Zmn6ZXNFQzU/vVRnOiplhRUMaPvddXuZAgth4OBAX9IMp5lLAJ74f0ly2mWxJFqUrb2161g=="*/

val apiSecret: String = project.findProperty("indoorAtlasApiSecret") as String?
    ?: "70WbWZwTfBKtbf6UyS0b4Z+ITFV4tc5SCSXz1o9+HKw0n0B5vcV7bhLRZcszdd/CbBlH4oCXVNf4NslE9jMW/mzsl4r/LHkBjA6Vk7hEtsPvlg0Tp/kVAouN0/fRlA=="

val backgroundReportEndPoint: String = project.findProperty("backgroundReportEndPoint") as String?
    ?: ""
android {
    namespace = "com.app.indooraits"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
       // applicationId = "com.app.indooraits"
        minSdk = 24
        //targetSdk = 36
        //versionCode = 1
        //versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resValue("string", "indooratlas_api_key", apiKey)
        resValue("string", "indooratlas_api_secret", apiSecret)
        resValue("string", "background_report_endpoint", backgroundReportEndPoint)
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
    buildFeatures {
        buildConfig = true
        resValues = true
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
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
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = "com.app"
                artifactId = "indooratlash"
                version = "1.0.0"

                if (components.findByName("release") != null) {
                    from(components["release"])
                }
            }
        }
    }
}