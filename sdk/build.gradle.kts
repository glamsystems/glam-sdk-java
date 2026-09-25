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
  fuzz.register("mappingIndex") {
    targetClass = "systems.glam.sdk.MappingIndexFuzz"
    seedCorpus = layout.projectDirectory.dir("src/test/resources/fuzz/mappingIndex")
    // the index is a few hundred bytes of JSON; headroom lets the mutator probe long
    // names and deep nesting without clipping the real seed
    maxLen = 4096
  }
}

// The jar embeds the ix-mapper mapping documents under glam/ix-mappings/{production,staging}:
// the generated documents of the TypeScript mapper package (packages/glam/ix-mapper-ts in
// glamsystems/glam, published through glamsystems/ix-mapper-ts), tracked under ix-mapper-ts/
// in the package's layout, where the monorepo's sync workflow writes them. They are copied
// at processResources time, so tests read the bytes the jar ships, beside an index the
// loader reads, since a jar cannot be listed. -PglamMappingsDir=<absolute path> points the
// build at another root with the package layout (a checkout of the package itself): the
// seam that lets regenerated documents face this validation before they are synced.
// `from()` on a directory that does not exist copies nothing without a word, and every
// published sdk jar before the embedding shipped empty for exactly that reason, so the
// index task refuses an empty set and the archive is checked after it is written.
val mappingsOverride = providers.gradleProperty("glamMappingsDir")
val mappingsRoot: File = mappingsOverride.map { File(it) }.getOrElse(rootDir.resolve("ix-mapper-ts"))
// one directory of documents per ix-mapper environment, named as GlamEnv.mappingEnvironment names them
val mappingEnvironments = listOf("production", "staging")
fun mappingSet(environment: String): File = mappingsRoot.resolve("src/generated/mapping/$environment")

// What the index records as the documents' source: the tracked directory, or the local root
// the build was pointed at.
val mappingsSource: Provider<String> = mappingsOverride.map { "local:$it" }.orElse("ix-mapper-ts")

val ixMappingsIndex = tasks.register("ixMappingsIndex") {
  description = "Writes glam/ix-mappings/index.json: each environment's embedded document file names and where they came from."
  val environments = mappingEnvironments
  val sets = environments.associateWith { mappingSet(it) }
  sets.forEach { (environment, dir) -> inputs.dir(dir).withPropertyName("${environment}Set") }
  val source = mappingsSource
  inputs.property("source", source)
  val indexFile = layout.buildDirectory.file("ix-mappings/index.json")
  outputs.file(indexFile)
  doLast {
    // written by a JSON serializer: the source may be a local path, which can carry any
    // character, and a hand-built string would break the index on a quote
    val index = linkedMapOf<String, Any>("source" to source.get())
    environments.forEach { environment ->
      val dir = sets.getValue(environment)
      val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".json") }.orEmpty().sortedBy { it.name }
      check(files.isNotEmpty()) { "No mapping documents under $dir; the sdk jar must not ship without the $environment set." }
      index[environment] = files.map { it.name }
    }
    val file = indexFile.get().asFile
    file.parentFile.mkdirs()
    file.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(index)) + "\n")
  }
}

tasks.named<ProcessResources>("processResources") {
  mappingEnvironments.forEach { environment ->
    from(mappingSet(environment)) {
      include("*.json")
      into("glam/ix-mappings/$environment")
    }
  }
  from(ixMappingsIndex) {
    into("glam/ix-mappings")
  }
}

tasks.named<Jar>("jar") {
  val environments = mappingEnvironments
  doLast {
    ZipFile(archiveFile.get().asFile).use { archive ->
      val entries = archive.entries().asSequence().map { it.name }.toList()
      for (environment in environments) {
        val embedded = entries.count { it.startsWith("glam/ix-mappings/$environment/") && it.endsWith(".json") }
        check(embedded > 0) { "${archiveFile.get().asFile.name} was written without any glam/ix-mappings/$environment/*.json entry." }
        logger.lifecycle("${archiveFile.get().asFile.name} embeds $embedded $environment mapping document(s).")
      }
      check("glam/ix-mappings/index.json" in entries) { "${archiveFile.get().asFile.name} was written without glam/ix-mappings/index.json." }
    }
  }
}

tasks.withType<Test>().configureEach {
  providers.gradleProperty("glamMappingsDir").orNull?.let { systemProperty("glam.mappings.dir", it) }
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
