/**
 * Maven Central publishing. The artifact ID is derived from the project path, dropping the `lang` grouping folder:
 * `:lang:cobol:frontend` -> `cobol-frontend`. Override it with `parzPublishing { artifactId = "..." }`.
 */
plugins {
    id("com.vanniktech.maven.publish")
}

val user = providers.gradleProperty("user").get()
val dev = providers.gradleProperty("dev").get()
val mail = providers.gradleProperty("mail").get()
val devURL = providers.gradleProperty("devURL").get()
val repo = providers.gradleProperty("repo").get()
val desc = providers.gradleProperty("description").get()
val inception = providers.gradleProperty("inception").get()

val parzPublishing = extensions.create<ParzPublishingExtension>("parzPublishing")
parzPublishing.artifactId.convention(
    project.path
        .removePrefix(":")
        .split(':')
        .dropWhile { it == "lang" }
        .joinToString("-"),
)

// The plugin renames Kotlin Multiplatform publications on each coordinates() call and can only do it once,
// so wait until the module's build script has had a chance to override the artifact ID
afterEvaluate {
    mavenPublishing {
        coordinates(project.group.toString(), parzPublishing.artifactId.get(), project.version.toString())
        pom {
            name = "$repo ${parzPublishing.artifactId.get()}"
        }
    }
}

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    pom {
        description = desc
        inceptionYear = inception
        url = "https://github.com/$user/$repo"
        licenses {
            license {
                name = "GNU AGPLv3"
                url = "https://github.com/$user/$repo/blob/main/LICENSE"
            }
        }
        developers {
            developer {
                name = dev
                email = mail
                url = devURL
            }
        }
        scm {
            url = "https://github.com/$user/$repo"
            connection = "scm:git:git://github.com/$user/$repo.git"
            developerConnection = "scm:git:ssh://github.com/$user/$repo.git"
        }
    }
}
