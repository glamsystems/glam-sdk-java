rootProject.name = "glam-sdk-java"

pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    val gprUser = providers.gradleProperty("savaGithubPackagesUsername")
      .orNull?.takeIf { it.isNotBlank() }
    val gprToken = providers.gradleProperty("savaGithubPackagesPassword")
      .orNull?.takeIf { it.isNotBlank() }
    if (gprUser != null && gprToken != null) {
      maven {
        name = "savaGithubPackages"
        url = uri("https://maven.pkg.github.com/sava-software/sava-build")
        credentials {
          username = gprUser
          password = gprToken
        }
      }
    }
  }
  // Resolve sava-build from GitHub Packages. Uncomment only while depending on an
  // unpublished sava-build change, then publish, bump the versions below, re-comment.
//  if (settingsDir.resolve("../sava-build").isDirectory) {
//    includeBuild("../sava-build")
//  }
}

plugins {
  id("software.sava.build") version "21.5.9"
  id("software.sava.build.feature.jdk-provisioning") version "21.5.9"
}

javaModules {
  directory(".") {
    group = "systems.glam"
    plugin("software.sava.build.java-module")
  }
}

// TEMPORARY: depends on unpublished ravina changes (BackoffConfig delay
// granularity, tx monitor delay guards). Publish ravina, bump
// solanaBOMVersion in gradle/sava.properties, then re-comment this.
// See "Changing a dependency" in AGENTS.md.
//includeBuild("../ravina")
//includeBuild("../idl-clients")
