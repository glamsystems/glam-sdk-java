testModuleInfo {
  requires("org.junit.jupiter.api")
  runtimeOnly("org.junit.jupiter.engine")
}

dependencies {
  project(":sdk")
}

dependencyAnalysis {
  issues {
    onAny {
      severity("ignore")
    }
  }
}
