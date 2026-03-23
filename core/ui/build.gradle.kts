plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.amberesaiae.melos.core.ui"
    compileSdk = 35
    defaultConfig { minSdk = 29; consumerProguardFiles("consumer-rules.pro") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17"; freeCompilerArgs += listOf("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api", "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi") }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:model"))
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.bundles.compose.ui)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.coil.compose)
    implementation(libs.coil.okhttp)
    implementation(libs.lifecycle.runtime.compose)
}
