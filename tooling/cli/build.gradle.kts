plugins {
    id("parz.jvm-library")
    application
    alias(libs.plugins.graalvm.native)
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

graalvmNative {
    toolchainDetection = true

    binaries.named("main") {
        imageName = "parz"
        mainClass = application.mainClass
        javaLauncher =
            javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(25)
                vendor = JvmVendorSpec.GRAAL_VM
                nativeImageCapable = true
            }
        buildArgs.add("--enable-native-access=ALL-UNNAMED")
    }
}
