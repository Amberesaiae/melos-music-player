plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.amberesaiae.melos.core.network"
    compileSdk = 35
    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            buildConfigField("Boolean", "DEBUG", "false")
        }
        debug {
            buildConfigField("Boolean", "DEBUG", "true")
        }
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:model"))
    
    // Networking - Retrofit 2.11.0
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    
    // OkHttp 4.12.0 with logging
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    
    // Kotlinx Serialization 1.6.3
    implementation(libs.kotlinx.serialization.json)
    
    // Kotlinx Coroutines 1.10.1
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    
    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    
    // Testing
    testImplementation(libs.bundles.testing.unit)
    testRuntimeOnly(libs.junit5.engine)
}
