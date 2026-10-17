
plugins {
    alias(libs.plugins.android.application)
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.soham.medicinereminderapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.soham.medicinereminderapp"
        minSdk = 24
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Android UI
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    // Room database
    implementation("androidx.room:room-runtime:2.8.0")
    implementation("androidx.room:room-ktx:2.8.0")
    ksp("androidx.room:room-compiler:2.8.0")

    // Lifecycle and coroutines
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")

    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.4.0")

    // CardView used by item_medicine.xml
    implementation("androidx.cardview:cardview:1.0.0")
// Background low-stock checks
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    // Unit and instrumentation tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
