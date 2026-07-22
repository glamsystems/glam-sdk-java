plugins {
  id("software.sava.build.feature.hardening")
}

testModuleInfo {
  requires("org.junit.jupiter.api")
  runtimeOnly("org.junit.jupiter.engine")
}

hardening {
  mutation.register("sdk") {
    mutators = "STRONGER,EXPERIMENTAL_NAKED_RECEIVER"
    // catch-all by exclusion, so a new hand-written class is mutated by
    // default instead of silently skipped
    targetClasses = listOf("systems.glam.sdk.*")
    excludedClasses = listOf(
      // generated per-program code: correctness belongs to idl-src-gen, and
      // mutating the boilerplate would bury the hand-written signal
      "systems.glam.sdk.idl.*.gen.*",
      // test sources share the recompiled root
      "systems.glam.sdk.*Test*",
      "systems.glam.sdk.*Fuzz*"
    )
    targetTests = "systems.glam.sdk.*Test*"
  }
}

tasks.named<Jar>("jar") {
  from("${rootDir}/glam/mapping-configs-v1") {
    include("**/*.json")
    into("glam/ix-mappings")
  }
}

dependencyAnalysis {
  issues {
    onAny {
      severity("ignore")
    }
  }
}

dependencies {
//  project(":idl-clients:idl-clients-bundle")
//  project(":idl-clients:idl-clients-spl")

//  project(":ravina:ravina-core")
//  project(":ravina:ravina-solana")
}
