plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jmailen.kotlinter")
    id("parz.docs")
}

val libs = the<VersionCatalogsExtension>().named("libs")

kotlin {
    jvmToolchain(26)
}

dependencies {
    testImplementation(libs.findLibrary("kotlin-test").get())
}

tasks.test {
    useJUnitPlatform()
}
