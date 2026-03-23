plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.amberesaiae.melos.feature.server"
    compileSdk = 35
    defaultConfig { minSdk = 29; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"; consumerProguardFiles("consumer-rules.pro") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17"; freeCompilerArgs += listOf("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api") }
    buildFeatures { compose = true }
    testOptions { unitTests.all { it.useJUnitPlatform() } }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.bundles.compose.ui)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.bundles.lifecycle)
    implementation(libs.bundles.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.crypto)
    implementation(libs.datastore.preferences)
    testImplementation(libs.bundles.testing.unit)
    testRuntimeOnly(libs.junit5.engine)
}
