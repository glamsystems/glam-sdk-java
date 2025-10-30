module systems.glam.sdk {
  exports systems.glam.sdk;
  exports systems.glam.sdk.idl.programs._commons;
  exports systems.glam.sdk.idl.programs.glam.protocol.gen.types;
  exports systems.glam.sdk.idl.programs.glam.protocol.gen;
  requires java.base;
  requires java.net.http;
  requires org.bouncycastle.provider;
  requires transitive software.sava.core;
  requires transitive software.sava.rpc;
  requires transitive software.sava.solana_programs;
  requires transitive software.sava.solana_web2;
  requires transitive systems.comodal.json_iterator;
  requires transitive systems.glam.ix_proxy;
}
