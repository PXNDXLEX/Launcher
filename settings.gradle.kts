pluginManagement {
    repositories {
        google()
        maven { url = uri("https://repo1.maven.org/maven2/") } // El espejo salvador
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven { url = uri("https://repo1.maven.org/maven2/") } // El espejo salvador
        mavenCentral()
    }
}
rootProject.name = "CarLauncher"
include(":app")