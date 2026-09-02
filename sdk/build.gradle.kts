import java.util.zip.ZipFile

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

// The jar embeds the mapping configs under glam/ix-mappings. The source directory is an
// untracked sparse checkout that only ./downloadMappings.sh creates, and `from()` on a
// directory that does not exist copies nothing without a word — every published sdk jar
// before this task shipped empty for exactly that reason. The download is therefore part
// of the jar's own graph, and the archive is checked after it is written.
val downloadMappings = tasks.register<Exec>("downloadMappings") {
  description = "Materializes the pinned ix-mapper-ts mapping configs under the untracked glam/ directory."
  workingDir = rootDir
  commandLine("./downloadMappings.sh")
}

tasks.named<Jar>("jar") {
  dependsOn(downloadMappings)
  val mappings = rootDir.resolve("glam/mapping-configs-v1")
  from(mappings) {
    include("**/*.json")
    into("glam/ix-mappings")
  }
  doFirst {
    val configs = mappings.listFiles { file -> file.isFile && file.name.endsWith(".json") }.orEmpty()
    check(configs.isNotEmpty()) {
      "No mapping configs under $mappings; the sdk jar must not ship without them (./downloadMappings.sh)."
    }
  }
  doLast {
    ZipFile(archiveFile.get().asFile).use { archive ->
      val embedded = archive.entries().asSequence()
          .count { entry -> entry.name.startsWith("glam/ix-mappings/") && entry.name.endsWith(".json") }
      check(embedded > 0) { "${archiveFile.get().asFile.name} was written without any glam/ix-mappings/*.json entry." }
      logger.lifecycle("${archiveFile.get().asFile.name} embeds $embedded mapping config(s) under glam/ix-mappings.")
    }
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
