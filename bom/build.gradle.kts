plugins {
    `java-platform`
    id("parz.publishing")
}

dependencies {
    constraints {
        api(project(":idl:model"))
    }
}
