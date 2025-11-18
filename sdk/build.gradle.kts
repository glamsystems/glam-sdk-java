testModuleInfo {
  requires("org.junit.jupiter.api")
  runtimeOnly("org.junit.jupiter.engine")
}

// Include mapping JSON files under ./glam/remapping_v1 in the published Jar
// They will be placed under glam/remapping_v1/ inside the jar, preserving the relative path.
tasks.named<Copy>("processResources") {
  from(rootDir) {
    include("glam/mapping-configs-v1/**")
  }
}
