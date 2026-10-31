# GLAM Java SDK

## Verifying GLAM artifacts

Every artifact published from this repository (`systems.glam:sdk`, `systems.glam:services`),
and every release of [ix-mapper-java](https://github.com/glamsystems/ix-mapper-java)'s
`systems.glam:ix-proxy` from 24.0.0 on (the 0.x releases predate the key and are unsigned),
is signed with the GLAM release key:

```
pub  ed25519 2025-06-27 [expires 2028-06-26]  James Edwards <james@glam.systems>
     58FD20C108FB491E2AB3AED4D43841539ABB88EA
```

This README is the key's record for consumers. A consumer's Gradle dependency verification
trusts it for the `systems.glam` group with

```xml
<trusted-key id="58FD20C108FB491E2AB3AED4D43841539ABB88EA" group="systems.glam"/>
```

and needs the public key in its keyring, since the fleet's policies keep key servers off:
append the block below to `gradle/verification-keyring.keys` (Gradle lists it under its long
id, `D43841539ABB88EA`); keyserver.ubuntu.com serves the same key.

```
-----BEGIN PGP PUBLIC KEY BLOCK-----

mDMEaF7LExYJKwYBBAHaRw8BAQdA9IszZsE+huoJ1Q+Dp8yE45K89OOG9t/WjjAE
ntUbfgS0IkphbWVzIEVkd2FyZHMgPGphbWVzQGdsYW0uc3lzdGVtcz6ImQQTFgoA
QRYhBFj9IMEI+0keKrOu1NQ4QVOau4jqBQJoXssTAhsDBQkFo5qABQsJCAcCAiIC
BhUKCQgLAgQWAgMBAh4HAheAAAoJENQ4QVOau4jq+0wA/j9SFWzXFqQL2ahwFY/G
g/sFf9pVDmFxixXnMP3eihq4AQDBt9be0TEZgZXRowjiP1KrT+F9Hgcp7FqLfoRw
yTZWCLg4BGheyxMSCisGAQQBl1UBBQEBB0B+0lPDPV1gioLzMEbhR51cLsS6Vh/d
hCwqpwqvV8q7UwMBCAeIfgQYFgoAJhYhBFj9IMEI+0keKrOu1NQ4QVOau4jqBQJo
XssTAhsMBQkFo5qAAAoJENQ4QVOau4jqmDUBAPn+y8XDgSRxw/mYaz+L+G67dRBD
r1y143PFwzig/fWTAP0QMSMoAbFzulwtvTZN9zEGe0wzfzrYZPpxCoPFxUCWCg==
=sOfE
-----END PGP PUBLIC KEY BLOCK-----
```

This repository's own policy is `gradle/verification-metadata.xml`, with the keys it names in
`gradle/verification-keyring.keys`; sava-build's README, "Verifying sava artifacts", holds the
rules it follows and how a refusal is handled.
