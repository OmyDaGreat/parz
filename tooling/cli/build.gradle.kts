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
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}
