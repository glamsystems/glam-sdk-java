testModuleInfo {
  requires("org.junit.jupiter.api")
  runtimeOnly("org.junit.jupiter.engine")
}

// Include mapping JSON files under ./glam/mapping-configs-v1 in the published Jar
// They will be placed under glam/ix-mappings/ inside the jar, preserving the relative path.
tasks.named<ProcessResources>("processResources") {
  from("${rootDir}/glam/mapping-configs-v1") {
    include("**/*.json")
    // Destination directory inside the jar
    into("glam/ix-mappings")
  }
}
