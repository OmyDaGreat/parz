plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.android.gradle.plugin)
    implementation(libs.maven.publish.gradle.plugin)
    implementation(libs.dokka.gradle.plugin)
    implementation(libs.kotlinter.gradle.plugin)
}
