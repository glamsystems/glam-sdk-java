testModuleInfo {
  requires("org.junit.jupiter.api")
  runtimeOnly("org.junit.jupiter.engine")
}

tasks.named<Jar>("jar") {
  from("${rootDir}/glam/mapping-configs-v1") {
    include("**/*.json")
    into("glam/ix-mappings")
  }
}
