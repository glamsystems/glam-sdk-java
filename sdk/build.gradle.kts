plugins {
  id("software.sava.build.feature.hardening")
}

testModuleInfo {
  requires("org.junit.jupiter.api")
  runtimeOnly("org.junit.jupiter.engine")
}

hardening {
  // 'Integ.java' is a git-ignored scratch file: present on a dev machine and
  // absent in CI, and it sits in systems.glam directly, which no suite targets
  recompileExcludes = listOf("Integ.java")
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
    declineExclusionAudit(
      "systems.glam.sdk.idl.*.gen.*",
      "Generated per-program IDL bindings. Their correctness is owned by " +
          "idl-src-gen, which generates and tests the emitter; mutating the " +
          "boilerplate here would measure the generator's output rather than " +
          "this repo's hand-written code, and would bury the hand-written signal."
    )
  }
  fuzz.register("mappingConfig") {
    targetClass = "systems.glam.sdk.proxy.MappingConfigFuzz"
    seedCorpus = layout.projectDirectory.dir("src/test/resources/fuzz/mappingConfig")
    // config files are a few KB of JSON; headroom lets the mutator probe deep
    // nesting and long literals without clipping the real seeds
    maxLen = 65536
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
