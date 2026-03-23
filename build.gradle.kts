// Top-level build file - configuration shared across all subprojects/modules.
plugins {
    alias(libs.plugins.android.application)     apply false
    alias(libs.plugins.android.library)         apply false
    alias(libs.plugins.kotlin.android)          apply false
    alias(libs.plugins.kotlin.jvm)              apply false
    alias(libs.plugins.kotlin.serialization)    apply false
    alias(libs.plugins.kotlin.compose)          apply false
    alias(libs.plugins.hilt)                    apply false
    alias(libs.plugins.ksp)                     apply false
    alias(libs.plugins.room.plugin)             apply false
    alias(libs.plugins.detekt)                  apply false
}
