// NebulaGram: resolve the kotlin-dsl toolchain from Maven Central first. The
// Gradle plugin portal redirects kotlin-compiler-embeddable to GitHub release
// downloads, which time out (504) on a cold CI cache.
pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}
