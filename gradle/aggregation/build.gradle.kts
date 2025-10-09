plugins {
  id("software.sava.build.feature.publish-maven-central")
}

dependencies {
  nmcpAggregation(project(":glam-java-sdk"))
}

tasks.register("publishToGitHubPackages") {
  group = "publishing"
  dependsOn(
    ":anchor-programs:publishMavenJavaPublicationToSavaGithubPackagesRepository"
  )
}
