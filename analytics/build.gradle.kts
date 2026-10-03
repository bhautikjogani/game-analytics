plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
    // NOTE: do NOT apply google-services here. It belongs to the consuming :app module.
}

android {
    namespace = "com.ogl.game.analytics"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    publishing {
        singleVariant("release") { withSourcesJar() }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Firebase: versions come from the BoM. Not exposed to consumers.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.process)
    api(libs.kotlinx.coroutines.android) // StateFlow / suspend functions are part of the public API

    testImplementation(libs.junit)
}

// ---------------------------------------------------------------- GitHub Packages publishing
// Coordinates and repo come from gradle.properties / -P flags / CI env, so nothing is hardcoded here.
val libGroup = providers.gradleProperty("analytics.group").getOrElse("com.example.games")
val libVersion = providers.gradleProperty("analytics.version").getOrElse("0.1.0")
val githubRepo = System.getenv("GITHUB_REPOSITORY")                    // set automatically in Actions
    ?: providers.gradleProperty("analytics.githubRepo").orNull          // e.g. "your-user/android-analytics"

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = libGroup
            artifactId = "analytics"
            version = libVersion
            afterEvaluate { from(components["release"]) }
        }
    }
    repositories {
        if (githubRepo != null) {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/$githubRepo")
                credentials {
                    username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                    password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}
