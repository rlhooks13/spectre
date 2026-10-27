# Staging test key

`staging-test.jks` signs `staging` builds only. It is **public on purpose**: committing it lets CI and every machine build staging APKs that install over each other.

- Store type PKCS12, alias `staging`, store and key password `spectre-staging` (see `app/build.gradle.kts`).
- Certificate: `CN=Spectre staging test key (not for release), O=Spectre`, valid until 2054. SHA-256 `AC:40:BD:1C:95:3C:C6:63:DD:43:03:A6:18:0C:3E:F0:A4:22:B1:24:BE:AC:47:F7:84:5C:2B:DA:38:18:D4:37`.
- Staging builds carry the `.staging` app id suffix, so this key can never update a release install.

Releases are signed only by the CI sign job, with the key in the `release` environment.
