plugins {
    id("org.jetbrains.dokka")
}

val dev = providers.gradleProperty("dev").get()
val mail = providers.gradleProperty("mail").get()

dokka {
    pluginsConfiguration.html {
        footerMessage.set("&copy; 2026 $dev <$mail>")
    }
}
