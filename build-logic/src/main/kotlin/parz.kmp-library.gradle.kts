/**
 * Kotlin Multiplatform library: JVM, Android, iOS and JS. Only for modules that consumers on other platforms need,
 * such as the IDL model, the IR core and runtimes. Compiler-side modules use `parz.jvm-library`.
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jmailen.kotlinter")
    id("parz.docs")
}

val libs = the<VersionCatalogsExtension>().named("libs")

kotlin {
    jvmToolchain(26)

    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    android {
        // :idl:model -> xyz.malefic.parz.idl.model
        namespace = "${project.group}${project.path.replace(':', '.')}"
        compileSdk =
            libs
                .findVersion("android-compileSdk")
                .get()
                .requiredVersion
                .toInt()
        minSdk =
            libs
                .findVersion("android-minSdk")
                .get()
                .requiredVersion
                .toInt()

        withJava()
        withHostTest {}
        withDeviceTest {}
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    js {
        nodejs()
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonTest.dependencies {
            implementation(libs.findLibrary("kotlin-test").get())
        }
    }
}
