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
    // includeBuild("../sava-build")
  }
}

plugins {
  id("software.sava.build") version "21.4.2"
}

javaModules {
  directory(".") {
    group = "systems.glam"
    plugin("software.sava.build.java-module")
  }
}

//includeBuild("../ravina")
includeBuild("../idl-clients")
