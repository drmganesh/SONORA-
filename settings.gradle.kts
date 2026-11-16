pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    maven {
      url = uri("third_party/newpipe-extractor/m2")
      content {
        includeVersion("com.github.TeamNewPipe", "NewPipeExtractor", "v0.27.1-sonora.1")
      }
    }
    google()
    mavenCentral()
    maven {
      url = uri("https://jitpack.io")
      content {
        includeGroupByRegex("com\\.github\\.TeamNewPipe.*")
        includeGroupByRegex("org\\.schabi\\.newpipe.*")
      }
    }
  }
}

rootProject.name = "SONORA"

include(":app")
