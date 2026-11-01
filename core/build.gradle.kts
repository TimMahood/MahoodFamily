plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mahood.family.core"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    api(libs.androidx.core.ktx)
    implementation(libs.androidx.work)
    implementation(libs.okhttp)
    implementation(libs.coroutines.play.services)
    implementation(libs.play.services.auth)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)
}
