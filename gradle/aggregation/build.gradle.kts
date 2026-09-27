plugins {
  id("software.sava.build.feature.publish-maven-central")
}

val publishedModules = setOf(
  "sdk",
  "services",
)

dependencies {
  for (module in publishedModules) {
    centralPortalAggregation(project(":$module"))
  }
}
