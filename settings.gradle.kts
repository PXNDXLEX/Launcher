pluginManagement {
    repositories {
        google()
        maven { url = uri("https://repo1.maven.org/maven2/") }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven { url = uri("https://repo1.maven.org/maven2/") }
        mavenCentral()
        
        // Repositorio oficial de Mapbox para descargar la librería
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
    }
}
rootProject.name = "CarLauncher"
include(":app")pluginManagement {
    repositories {
        google()
        maven { url = uri("https://repo1.maven.org/maven2/") }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven { url = uri("https://repo1.maven.org/maven2/") }
        mavenCentral()
        
        // Repositorio oficial de Mapbox para descargar la librería
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
    }
}
rootProject.name = "CarLauncher"
include(":app")