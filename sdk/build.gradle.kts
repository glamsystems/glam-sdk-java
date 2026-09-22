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

// The jar embeds the ix-mapper mapping configs under glam/ix-mappings/{production,staging}:
// the two directories at the repository root that glamsystems/glam projects here (its
// scripts/ix-mapper-gen sets, generated from its managed IDL stores). They are copied at
// processResources time, so tests read the bytes the jar ships, beside an index the loader
// reads, since a jar cannot be listed. `from()` on a directory that does not exist copies
// nothing without a word, and every published sdk jar before the embedding shipped empty for
// exactly that reason, so the index task refuses an empty set and the archive is checked
// after it is written.
val mappingSets = linkedMapOf(
  "production" to rootDir.resolve("mapping-configs-v1"),
  "staging" to rootDir.resolve("mapping-configs-v1-staging"),
)

val ixMappingsIndex = tasks.register("ixMappingsIndex") {
  description = "Writes glam/ix-mappings/index.json: each environment's embedded config file names and the glam commit they were projected from."
  val sets = mappingSets
  val root = rootDir
  sets.values.forEach { inputs.dir(it) }
  val indexFile = layout.buildDirectory.file("ix-mappings/index.json")
  outputs.file(indexFile)
  doLast {
    // The sync that projects the sets records the glam commit in its message.
    val log = ProcessBuilder(listOf("git", "log", "-1", "--format=%B", "--") + sets.values.map { it.relativeTo(root).path })
        .directory(root)
        .redirectErrorStream(true)
        .start()
    val message = log.inputStream.bufferedReader().readText()
    val source = if (log.waitFor() == 0) {
      Regex("Synced from glamsystems/glam ([0-9a-f]{40})").find(message)?.groupValues?.get(1)?.let { "glamsystems/glam@$it" } ?: "unrecorded"
    } else {
      "unrecorded"
    }
    val entries = sets.entries.joinToString(",\n") { (env, dir) ->
      val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".json") }.orEmpty().sortedBy { it.name }
      check(files.isNotEmpty()) { "No mapping configs under $dir; the sdk jar must not ship without the $env set." }
      "  \"$env\": [" + files.joinToString(", ") { "\"" + it.name + "\"" } + "]"
    }
    val file = indexFile.get().asFile
    file.parentFile.mkdirs()
    file.writeText("{\n  \"source\": \"$source\",\n$entries\n}\n")
  }
}

tasks.named<ProcessResources>("processResources") {
  mappingSets.forEach { (env, dir) ->
    from(dir) {
      include("*.json")
      into("glam/ix-mappings/$env")
    }
  }
  from(ixMappingsIndex) {
    into("glam/ix-mappings")
  }
}

tasks.named<Jar>("jar") {
  doLast {
    ZipFile(archiveFile.get().asFile).use { archive ->
      val entries = archive.entries().asSequence().map { it.name }.toList()
      for (env in listOf("production", "staging")) {
        val embedded = entries.count { it.startsWith("glam/ix-mappings/$env/") && it.endsWith(".json") }
        check(embedded > 0) { "${archiveFile.get().asFile.name} was written without any glam/ix-mappings/$env/*.json entry." }
        logger.lifecycle("${archiveFile.get().asFile.name} embeds $embedded $env mapping config(s).")
      }
      check("glam/ix-mappings/index.json" in entries) { "${archiveFile.get().asFile.name} was written without glam/ix-mappings/index.json." }
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
