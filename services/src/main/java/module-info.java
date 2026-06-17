
module systems.glam.services {
  requires java.logging;
  requires java.net.http;

  requires transitive systems.comodal.json_iterator;

  requires transitive software.sava.core;
  requires transitive software.sava.rpc;

  requires transitive software.sava.idl.clients.bundle;
  requires transitive software.sava.idl.clients.spl;

  requires transitive software.sava.kms_core;
  requires transitive software.sava.ravina_core;
  requires transitive software.sava.ravina_solana;

  requires transitive systems.glam.sdk;

  // Database
  requires java.sql;
  requires com.zaxxer.hikari;

  exports systems.glam.services.db.sql;
  exports systems.glam.services.config;
  exports systems.glam.services.execution;
  exports systems.glam.services.fulfillment.accounting;
  exports systems.glam.services.fulfillment.config;
  exports systems.glam.services.fulfillment;
  exports systems.glam.services.integrations.kamino;
  exports systems.glam.services.integrations;
  exports systems.glam.services.io;
  exports systems.glam.services.mints;
  exports systems.glam.services.oracles.scope;
  exports systems.glam.services.rpc;
  exports systems.glam.services.state;
  exports systems.glam.services;
}
