module systems.glam.services {
  requires java.net.http;

  requires transitive systems.comodal.json_iterator;

  requires transitive software.sava.core;
  requires transitive software.sava.rpc;
  requires software.sava.solana_programs;
  requires software.sava.solana_web2;

  requires software.sava.idl.clients.core;
  requires transitive software.sava.idl.clients.drift;
  requires transitive software.sava.idl.clients.kamino;
  requires software.sava.idl.clients.oracles;
  requires transitive software.sava.idl.clients.spl;

  requires transitive software.sava.kms_core;
  requires transitive software.sava.ravina_core;
  requires transitive software.sava.ravina_solana;

  requires transitive systems.glam.sdk;

  exports systems.glam.services.config;
  exports systems.glam.services.execution;
  exports systems.glam.services.fulfillment.config;
  exports systems.glam.services.fulfillment.accounting;
  exports systems.glam.services.fulfillment;
  exports systems.glam.services.io;
  exports systems.glam.services.integrations.drift;
  exports systems.glam.services.integrations.kamino;
  exports systems.glam.services.integrations;
  exports systems.glam.services.mints;
  exports systems.glam.services.oracles.scope;
  exports systems.glam.services.oracles.scope.parsers;
  exports systems.glam.services.pricing.accounting;
  exports systems.glam.services.pricing;
  exports systems.glam.services.rpc;
  exports systems.glam.services;


}
