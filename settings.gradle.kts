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
        // 1. Repositorio oficial de Mapbox como PRIORIDAD VIP para evitar conflictos
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            credentials {
                username = "mapbox"
                // Usa el token secreto (sk...) que guardamos en GitHub Actions
                password = System.getenv("MAPBOX_DOWNLOADS_TOKEN") ?: ""
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
        
        // 2. Tiendas generales de Android
        google()
        mavenCentral()
    }
}
rootProject.name = "CarLauncher"
include(":app")