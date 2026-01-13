module systems.glam.services {
  requires java.net.http;

  requires transitive systems.comodal.json_iterator;

  requires transitive software.sava.core;
  requires transitive software.sava.rpc;
  requires software.sava.solana_web2;

  requires transitive software.sava.kms_core;

  requires transitive software.sava.ravina_core;
  requires transitive software.sava.ravina_solana;

  requires software.sava.idl.clients.oracles;
  requires software.sava.idl.clients.spl;
  requires transitive software.sava.idl.clients.kamino;

  requires transitive systems.glam.sdk;

  exports systems.glam.services.execution;
  exports systems.glam.services.fulfillment;
  exports systems.glam.services.io;
  exports systems.glam.services.oracles.scope;
  exports systems.glam.services.oracles.scope.parsers;
  exports systems.glam.services.tokens;
  exports systems.glam.services.config;
  exports systems.glam.services.fulfillment.config;
  exports systems.glam.services.fulfillment.accounting;
  exports systems.glam.services.pricing.accounting;
  exports systems.glam.services;
}
