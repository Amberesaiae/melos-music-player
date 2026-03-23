plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.amberesaiae.melos"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.amberesaiae.melos"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures { compose = true; buildConfig = true }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }

    testOptions {
        unitTests { isIncludeAndroidResources = true; all { it.useJUnitPlatform() } }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.bundles.lifecycle)
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.bundles.compose.ui)
    implementation(libs.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.work.runtime.ktx)
    implementation(libs.bundles.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.datastore.preferences)
    implementation(libs.datastore.proto)
    implementation(libs.crypto)
    implementation(libs.coil.compose)
    implementation(libs.coil.okhttp)
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:player"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":feature:library"))
    implementation(project(":feature:now-playing"))
    implementation(project(":feature:playlists"))
    implementation(project(":feature:search"))
    implementation(project(":feature:server"))
    implementation(project(":feature:settings"))
    implementation(project(":platform:notifications"))
    implementation(project(":platform:android-auto"))
    testImplementation(libs.bundles.testing.unit)
    testRuntimeOnly(libs.junit5.engine)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.compose.ui.test.junit4)
}
