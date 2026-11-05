pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Mapbox SDK v11 — soporta token en CI o repositorio público de Mapbox v11
        val mapboxToken = System.getenv("MAPBOX_DOWNLOADS_TOKEN") ?: ""
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            if (mapboxToken.isNotEmpty()) {
                authentication {
                    create<BasicAuthentication>("basic")
                }
                credentials {
                    username = "mapbox"
                    password = mapboxToken
                }
            }
        }
    }
}
rootProject.name = "CarLauncher"
include(":app")
