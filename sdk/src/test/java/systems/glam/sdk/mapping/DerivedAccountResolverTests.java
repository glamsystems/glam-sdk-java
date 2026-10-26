package systems.glam.sdk.mapping;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import systems.glam.ix.proxy.SuppliedAccountsRequest.Account;
import systems.glam.ix.proxy.SuppliedAccountsRequest.Arg;
import systems.glam.ix.proxy.SuppliedAccountsRequest.Const;
import systems.glam.ix.proxy.SuppliedAccountsRequest.Derivation;
import systems.glam.ix.proxy.SuppliedAccountsRequest.Role;
import systems.glam.sdk.GlamAccounts;
import systems.glam.sdk.idl.programs.glam.staging.bridge.gen.ExtBridgePDAs;

import java.util.List;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;
import static systems.glam.sdk.mapping.DerivedAccountResolver.*;
import static systems.glam.sdk.tests.MappingFixtures.*;

/// A seat's derivation, as the mapper hands it over with the addresses it placed, finds the handler's account
/// under the handler's program. The oracle is the staging bridge's generated PDA helper, which spells the
/// same seeds from the IDL without the resolver.
final class DerivedAccountResolverTests {

  private static final PublicKey EXT_BRIDGE = GlamAccounts.MAIN_NET_STAGING.bridgeIntegrationProgram();
  private static final PublicKey STATE_KEY = fromBase58Encoded("9fkan2jCsS7Xq3fLqgxgZT5pDCbj2MhQ5MAoEKSHrcAT");
  private static final byte[] BRIDGE_ROUTES = "bridge-routes".getBytes(US_ASCII);
  /// The CCTP provider bit, 1, as the u16 seed the handler spells it with.
  private static final byte[] CCTP = {1, 0};
  /// The LayerZero OFT provider bit, 4.
  private static final byte[] LAYERZERO_OFT = {4, 0};

  /// The seat as the synced CCTP document states it: `"bridge-routes"`, the state the mapper placed at
  /// account index 0, the provider bit.
  private static Role bridgeRoutes(final PublicKey program, final PublicKey state, final byte[] protocol) {
    return new Role("bridge_routes", List.of(), false,
        new Derivation(program, List.of(new Const(BRIDGE_ROUTES), new Account(state), new Const(protocol))));
  }

  /// A seat derived from an argument of the instruction data, which this resolver does not read.
  private static Role bridgeSession() {
    return new Role("bridge_session", List.of(), false,
        new Derivation(EXT_BRIDGE, List.of(new Const("bridge-session".getBytes(US_ASCII)), new Account(STATE_KEY), new Arg("args.transfer_id"))));
  }

  @Test
  void aSeatIsTheProgramDerivedAddressOfItsSeedsUnderItsProgram() {
    final var cctpRoutes = ExtBridgePDAs.bridgeRoutes1PDA(EXT_BRIDGE, STATE_KEY).publicKey();
    assertEquals(cctpRoutes, derive(bridgeRoutes(EXT_BRIDGE, STATE_KEY, CCTP)));
    // the provider seed selects the set
    assertEquals(ExtBridgePDAs.bridgeRoutes2PDA(EXT_BRIDGE, STATE_KEY).publicKey(), derive(bridgeRoutes(EXT_BRIDGE, STATE_KEY, LAYERZERO_OFT)));
    // the account seed is the address the mapper placed: another vault's state, another set
    assertEquals(ExtBridgePDAs.bridgeRoutes1PDA(EXT_BRIDGE, key(9)).publicKey(), derive(bridgeRoutes(EXT_BRIDGE, key(9), CCTP)));
    // the program is the derivation's: the same seeds under another program find another address
    final var protocol = GlamAccounts.MAIN_NET_STAGING.protocolProgram();
    assertEquals(ExtBridgePDAs.bridgeRoutes1PDA(protocol, STATE_KEY).publicKey(), derive(bridgeRoutes(protocol, STATE_KEY, CCTP)));
    assertNotEquals(cctpRoutes, derive(bridgeRoutes(protocol, STATE_KEY, CCTP)));
    // the seeds in the order stated
    final var swapped = new Role("bridge_routes", List.of(), false,
        new Derivation(EXT_BRIDGE, List.of(new Account(STATE_KEY), new Const(BRIDGE_ROUTES), new Const(CCTP))));
    assertEquals(PublicKey.findProgramAddress(List.of(STATE_KEY.toByteArray(), BRIDGE_ROUTES, CCTP), EXT_BRIDGE).publicKey(), derive(swapped));
    assertNotEquals(cctpRoutes, derive(swapped));
    // no seed at all is a derivation too: the program's address under its bump alone
    final var bare = new Role("bare", List.of(), false, new Derivation(EXT_BRIDGE, List.of()));
    assertEquals(PublicKey.findProgramAddress(List.of(), EXT_BRIDGE).publicKey(), derive(bare));
  }

  @Test
  void onlyRolesTheDocumentDerivesCompletelyAreServed() {
    final var cctp = bridgeRoutes(EXT_BRIDGE, STATE_KEY, CCTP);
    final var layerzero = bridgeRoutes(EXT_BRIDGE, STATE_KEY, LAYERZERO_OFT);
    assertTrue(serves(List.of(cctp)));
    assertTrue(serves(List.of(cctp, layerzero)));
    assertFalse(serves(List.of()), "no role, nothing to derive");
    final var listed = new Role(OrcaOracleResolver.ASSET_ORACLE, List.of(USDC), false);
    assertFalse(serves(List.of(listed)), "a listed role states no derivation");
    assertFalse(serves(List.of(cctp, listed)), "a derived seat beside a listed role is not served");
    assertFalse(serves(List.of(listed, cctp)));
    final var session = bridgeSession();
    assertFalse(serves(List.of(session)), "an argument seed needs the instruction data");
    assertFalse(serves(List.of(cctp, session)));
    assertTrue(derivable(cctp.derivation()));
    assertFalse(derivable(null));
    assertFalse(derivable(session.derivation()));

    var refused = assertThrows(IllegalArgumentException.class, () -> derive(listed));
    assertEquals("The document states no derivation for asset_oracle.", refused.getMessage());
    refused = assertThrows(IllegalArgumentException.class, () -> derive(session));
    assertEquals("bridge_session derives from argument args.transfer_id of the instruction data, which this supplier does not read.", refused.getMessage());
    refused = assertThrows(IllegalArgumentException.class, () -> resolve(List.of(cctp, session)));
    assertEquals("bridge_session derives from argument args.transfer_id of the instruction data, which this supplier does not read.", refused.getMessage());
  }

  @Test
  void theRolesResolveInTheirOrder() {
    final var cctp = bridgeRoutes(EXT_BRIDGE, STATE_KEY, CCTP);
    final var layerzero = bridgeRoutes(EXT_BRIDGE, STATE_KEY, LAYERZERO_OFT);
    final var cctpRoutes = ExtBridgePDAs.bridgeRoutes1PDA(EXT_BRIDGE, STATE_KEY).publicKey();
    final var layerzeroRoutes = ExtBridgePDAs.bridgeRoutes2PDA(EXT_BRIDGE, STATE_KEY).publicKey();
    assertEquals(List.of(cctpRoutes, layerzeroRoutes), resolve(List.of(cctp, layerzero)));
    assertEquals(List.of(layerzeroRoutes, cctpRoutes), resolve(List.of(layerzero, cctp)));
    assertEquals(List.of(), resolve(List.of()));
    assertThrows(UnsupportedOperationException.class, () -> resolve(List.of(cctp)).add(STATE_KEY));
  }
}
