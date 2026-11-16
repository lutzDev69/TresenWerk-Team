// Oberste Build-Datei: legt nur fest, welche Plugins es im Projekt gibt.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
