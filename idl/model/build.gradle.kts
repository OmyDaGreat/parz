plugins {
    id("parz.kmp-library")
    id("parz.publishing")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.bignum)
        }
    }
}
