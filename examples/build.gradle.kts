// The examples ship no tests. On a dev machine src/test holds git-ignored scratch programs
// (Integ.java, run from the IDE), which gives the test task sources but nothing to discover.
// JUnit is declared as in sdk and services because the launcher the build adds has no version
// of its own: it gets one through junit-jupiter-api, the JUnit artifact the BOM pins.
testModuleInfo {
  requires("org.junit.jupiter.api")
  runtimeOnly("org.junit.jupiter.engine")
}

tasks.test {
  failOnNoDiscoveredTests = false
}

dependencyAnalysis {
  issues {
    onAny {
      severity("ignore")
    }
  }
}
