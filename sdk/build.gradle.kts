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
}

// The jar embeds the ix-mapper mapping documents under glam/ix-mappings/<environment>. The
// source directory is an untracked sparse checkout that only ./downloadMappings.sh creates,
// and `from()` on a directory that does not exist copies nothing without a word — every
// published sdk jar before this task shipped empty for exactly that reason. The download is
// therefore part of the jar's own graph, and the archive is checked after it is written.
val downloadMappings = tasks.register<Exec>("downloadMappings") {
  description = "Materializes the pinned ix-mapper-ts mapping documents under the untracked glam/ directory."
  workingDir = rootDir
  commandLine("./downloadMappings.sh")
}

tasks.named<Jar>("jar") {
  dependsOn(downloadMappings)
  val mappings = rootDir.resolve("glam/src/generated/mapping")
  // one directory of documents per ix-mapper environment, named as GlamEnv.mappingEnvironment
  // names them; local to the task so the configuration cache can serialize its actions
  val mappingEnvironments = listOf("production", "staging")
  from(mappings) {
    include("*/*.json")
    into("glam/ix-mappings")
  }
  doFirst {
    for (environment in mappingEnvironments) {
      val documents = mappings.resolve(environment).listFiles { file -> file.isFile && file.name.endsWith(".json") }.orEmpty()
      check(documents.isNotEmpty()) {
        "No mapping documents under ${mappings.resolve(environment)}; the sdk jar must not ship without them (./downloadMappings.sh)."
      }
    }
  }
  doLast {
    ZipFile(archiveFile.get().asFile).use { archive ->
      val entries = archive.entries().asSequence().map { entry -> entry.name }.toList()
      for (environment in mappingEnvironments) {
        val prefix = "glam/ix-mappings/$environment/"
        val embedded = entries.count { name -> name.startsWith(prefix) && name.endsWith(".json") }
        check(embedded > 0) { "${archiveFile.get().asFile.name} was written without any $prefix*.json entry." }
        logger.lifecycle("${archiveFile.get().asFile.name} embeds $embedded mapping document(s) under $prefix.")
      }
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
