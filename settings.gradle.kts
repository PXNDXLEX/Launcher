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
        // Mapbox SDK — token inyectado como variable de entorno (nunca en git)
        // En GitHub Actions: secret MAPBOX_DOWNLOADS_TOKEN
        // En local: agrega en ~/.gradle/gradle.properties → MAPBOX_DOWNLOADS_TOKEN=sk.eyJ1...
        val mapboxToken = System.getenv("MAPBOX_DOWNLOADS_TOKEN") ?: ""
        if (mapboxToken.isNotEmpty()) {
            maven {
                url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
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