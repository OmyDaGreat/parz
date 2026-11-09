plugins {
    id("parz.jvm-library")
    application
}

dependencies {
    implementation(project(":idl:syntax"))
    implementation(libs.bundles.jline)
}

application {
    mainClass.set("xyz.malefic.parz.tooling.cli.MainKt")
    applicationName = "parz"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}
