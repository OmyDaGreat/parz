import org.gradle.api.provider.Property

/**
 * Settings for `parz.publishing`.
 */
abstract class ParzPublishingExtension {
    /**
     * The Maven artifact ID. Defaults to the project path without the `lang` grouping folder.
     */
    abstract val artifactId: Property<String>
}
