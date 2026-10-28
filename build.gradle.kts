/*
 * Framecraft root build: declares plugin versions once so every module resolves the same toolchain.
 */

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlinter) apply false
    alias(libs.plugins.detekt) apply false
}
