plugins {
    id("parz.docs")
}

dependencies {
    dokka(project(":idl:model"))
    dokka(project(":idl:syntax"))
    dokka(project(":idl:encoding"))
}
