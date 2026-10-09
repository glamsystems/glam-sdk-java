plugins {
  id("software.sava.build.feature.hardening")
}

testModuleInfo {
  requires("org.junit.jupiter.api")
  runtimeOnly("org.junit.jupiter.engine")
}

// Git-ignored scratch programs (Integ.java and the like, run from the IDE) live in
// src/scratch/java, as package-private classes in the package whose members they reach for.
// The module-testing plugin patches this suite into the module once that folder exists, so
// they compile as part of it; being neither main nor test, they are no input to a mutation
// suite, a fuzz target or a certification, and check never runs them.
testing {
  suites {
    register<JvmTestSuite>("scratch") {
      // main() programs, not tests: nothing to discover, and an empty run fails
      targets.configureEach { testTask.configure { enabled = false } }
    }
  }
}

tasks.withType<Test>().configureEach {
  systemProperty("java.util.logging.config.file", layout.projectDirectory.file("src/test/resources/logging.properties").asFile.absolutePath)
}

hardening {
  fuzz.register("accountData") {
    targetClass = "systems.glam.services.io.AccountDataFuzz"
    seedCorpus = layout.projectDirectory.dir("src/test/resources/fuzz/accountData")
    // the decompression-bomb seed is 16KB and expands to 16MiB; the read cap
    // rejects it, so this bounds the *file* size the mutator can grow a seed to
    maxLen = 65536
  }
  fuzz.register("minGlamStateAccount") {
    targetClass = "systems.glam.services.state.MinGlamStateAccountFuzz"
    seedCorpus = layout.projectDirectory.dir("src/test/resources/fuzz/minGlamStateAccount")
    // the mainnet state account is 8200 bytes; headroom lets the mutator grow
    // the nested ACL sections and probe over-long length prefixes
    maxLen = 16384
  }
  mutation.register("services") {
    // EXPERIMENTAL_BIG_INTEGER left the set on 2026-10-09: its re-trial generated no mutants
    // here, the BigInteger liquidity totals it was enabled for having moved to
    // vault-stat-service; the trial numbers are in config/pitest/README.md
    mutators = "STRONGER,EXPERIMENTAL_NAKED_RECEIVER,EXPERIMENTAL_BIG_DECIMAL"
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
      "systems.glam.services.tests.*"
    )
    targetTests = "systems.glam.services.*Test*"
  }
}

dependencyAnalysis {
  issues {
    // the scratch suite is git-ignored local code: analysing it would make check compile it
    ignoreSourceSet("scratch")
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
