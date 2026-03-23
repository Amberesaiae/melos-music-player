plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room.plugin)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.amberesaiae.melos.core.database"
    compileSdk = 35
    defaultConfig { minSdk = 29; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"; consumerProguardFiles("consumer-rules.pro") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.all { it.useJUnitPlatform() } }
}

room { schemaDirectory("$projectDir/schemas") }

dependencies {
    implementation(project(":core:model"))
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)
    testImplementation(libs.room.testing)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.bundles.coroutines)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.bundles.testing.unit)
    testRuntimeOnly(libs.junit5.engine)
}
