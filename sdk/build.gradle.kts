testModuleInfo {
  requires("org.junit.jupiter.api")
  runtimeOnly("org.junit.jupiter.engine")
}

tasks.named<Jar>("jar") {
  from("${rootDir}/glam/mapping-configs-v1") {
    include("**/*.json")
    into("glam/ix-mappings")
  }
}

//dependencyAnalysis {
//  issues {
//    onAny {
//      severity("ignore")
//    }
//  }
//}

dependencies {
//  project(":idl-clients:idl-clients-kamino")
//  project(":idl-clients:idl-clients-loopscale")
//  project(":idl-clients:idl-clients-phoenix")

//  project(":ravina:ravina-core")
//  project(":ravina:ravina-solana")
}
