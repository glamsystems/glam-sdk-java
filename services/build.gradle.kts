plugins {
  id("software.sava.build.feature.hardening")
}

testModuleInfo {
  requires("org.junit.jupiter.api")
  runtimeOnly("org.junit.jupiter.engine")
}

hardening {
  mutation.register("services") {
    // catch-all by exclusion, so a new class is mutated by default instead of
    // silently skipped
    targetClasses = listOf("systems.glam.services.*")
    excludedClasses = listOf(
      // test sources share the recompiled root; 'tests' holds shared helpers
      // (ResourceUtil) that no *Test* pattern matches
      "systems.glam.services.*Test*",
      "systems.glam.services.*Fuzz*",
      "systems.glam.services.tests.*",
      // 'Integ.*' scratch files are git-ignored: present on a dev machine and
      // absent in CI, so mutating them would make the baseline
      // machine-dependent. Exact names — Integ* would also match
      // IntegLookupTableCache / IntegrationServiceContext.
      "systems.glam.services.Integ",
      "systems.glam.services.Integ\$*",
      "systems.glam.services.oracles.scope.Integ",
      "systems.glam.services.oracles.scope.Integ\$*"
    )
    targetTests = "systems.glam.services.*Test*"
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
  project(":sdk")

//  project(":idl-clients:idl-clients-bundle")
//  project(":idl-clients:idl-clients-spl")

//  project(":ravina:ravina-core")
//  project(":ravina:ravina-solana")
}
