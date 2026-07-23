plugins {
  id("software.sava.build.feature.hardening")
}

testModuleInfo {
  requires("org.junit.jupiter.api")
  runtimeOnly("org.junit.jupiter.engine")
}

hardening {
  // git-ignored scratch files: absent in CI, and their dependencies drift out
  // of the classpath — excluding them from the tool recompiles restores parity
  recompileExcludes = listOf("Integ.java")
  mutation.register("services") {
    mutators = "STRONGER,EXPERIMENTAL_NAKED_RECEIVER,EXPERIMENTAL_BIG_INTEGER,EXPERIMENTAL_BIG_DECIMAL"
    // trialed threads = 8 on 2026-07-23: 3m32s vs ~2m04s at the 4-thread
    // default, timed-out 67 -> 69. This suite is timing-heavy (await/signal
    // tests), so oversubscription inflates the very tests PIT reruns most.
    // Slowest quiet-run test is 0.58s and only one exceeds 0.5s, so PIT's
    // 4s flat timeout floor is ~7x slack; factor 2 keeps headroom
    // proportional to each test's own runtime under minion load. If
    // SURVIVED->TIMED_OUT churn ever shows up in the ratchet, raise the
    // constant before suspecting the code.
    timeoutFactor = 2.0
    timeoutConst = 1500L
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
