module systems.glam.sdk {
  exports systems.glam.sdk.idl.programs._commons;
  exports systems.glam.sdk.idl.programs.glam.protocol.anchor.types;
  exports systems.glam.sdk.idl.programs.glam.protocol.anchor;
  requires java.base;
  requires java.net.http;
  requires transitive software.sava.core;
  requires transitive software.sava.rpc;
  requires transitive systems.comodal.json_iterator;
}
