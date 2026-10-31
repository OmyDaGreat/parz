plugins {
    `java-platform`
    id("parz.publishing")
}

dependencies {
    constraints {
        api(project(":idl:model"))
        api(project(":idl:syntax"))
    }
}
