plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.amberesaiae.melos.platform.auto"
    compileSdk = 35
    defaultConfig { minSdk = 29; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"; consumerProguardFiles("consumer-rules.pro") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.all { it.useJUnitPlatform() } }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:player"))
    implementation(project(":platform:notifications"))
    implementation(libs.car.app)
    implementation(libs.car.app.projected)
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.bundles.coroutines)
    testImplementation(libs.bundles.testing.unit)
    testRuntimeOnly(libs.junit5.engine)
}
