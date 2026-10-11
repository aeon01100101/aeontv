include(":app")

pluginManagement {
    repositories {
        // Local markers for plugins whose portal markers mis-resolve in this environment
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
