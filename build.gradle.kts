plugins {
    id("parz.docs")
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
}

dependencies {
    dokka(project(":idl:model"))
    dokka(project(":idl:syntax"))
}
