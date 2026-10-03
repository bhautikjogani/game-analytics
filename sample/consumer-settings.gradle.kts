// INTEGRATION EXAMPLE: add to each GAME's settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/YOUR_USER/android-analytics")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

// Then in the game's app/build.gradle.kts:
//   plugins { alias(libs.plugins.google.services) }      // google-services.json lives in the game
//   dependencies { implementation("com.yourname.games:analytics:1.0.0") }
