plugins {
    id("parz.kmp-library")
    id("parz.publishing")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":idl:model"))
        }
    }
}
