rootProject.name = "glam-sdk-java"

pluginManagement {
  repositories {
    gradlePluginPortal()
    maven {
      name = "savaGithubPackages"
      url = uri("https://maven.pkg.github.com/sava-software/sava-build")
      credentials(PasswordCredentials::class)
    }
  }
//  includeBuild("../sava-build")
}

plugins {
  id("software.sava.build")  version "0.2.5"
}

javaModules {
  directory(".") {
    group = "systems.glam"
    plugin("software.sava.build.java-module")
  }
}
