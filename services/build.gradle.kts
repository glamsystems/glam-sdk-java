testModuleInfo {
  requires("org.junit.jupiter.api")
  runtimeOnly("org.junit.jupiter.engine")
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
//  project(":sava:sava-core")
//  project(":sava:sava-rpc")

//  project(":idl-clients:idl-clients-bundle")
//  project(":idl-clients:idl-clients-spl")

//  project(":ravina:ravina-core")
//  project(":ravina:ravina-solana")
}
