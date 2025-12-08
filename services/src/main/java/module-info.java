module systems.glam.services {
  requires java.net.http;

  requires transitive systems.comodal.json_iterator;

  requires transitive software.sava.core;
  requires software.sava.rpc;
  requires software.sava.ravina_core;
  requires transitive software.sava.ravina_solana;
  requires transitive software.sava.idl.clients.kamino;
//  requires systems.glam.sdk;
}
