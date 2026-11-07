plugins {
    id("parz.jvm-library")
    application
}

dependencies {
    implementation(project(":idl:syntax"))
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
