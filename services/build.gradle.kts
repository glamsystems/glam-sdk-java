testModuleInfo {
  requires("org.junit.jupiter.api")
  runtimeOnly("org.junit.jupiter.engine")
}

//dependencyAnalysis {
//  issues {
//    onAny {
//      severity("ignore")
//    }
//  }
//}

dependencies {
  project(":sdk")

//  project(":idl-clients:idl-clients-kamino")
//  project(":idl-clients:idl-clients-loopscale")
//  project(":ravina:ravina-core")
//  project(":ravina:ravina-solana")
}
