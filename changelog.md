# Changelog

This file contains all the notable changes done to the Ballerina `sap.jco` package through the releases.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Added `destinationId` parameter to the `Client.init` function, allowing an explicit name to be assigned to the RFC destination. This is required when a `Listener` references the client as its `repositoryDestination`.
- Added `connectionCount` field to `ServerConfig` to control the maximum number of concurrent JCo server connections (maps to `jco.server.connection_count`, default `2`).
- Added `repositoryDestination` field to `ServerConfig` to specify the RFC destination used by the JCo server to look up IDoc metadata. The value must match the `destinationId` of an already-initialised `Client`.
- Introduced distinct error types aligned with Ballerina conventions: `ConnectionError`, `LogonError`, `ResourceError`, `SystemError`, `AbapApplicationError`, `JCoError`, `IDocError`, `ParameterError`, and `ConfigurationError`. All are members of the existing `Error` union.
- Added `JCoErrorDetail` and `AbapApplicationErrorDetail` record types that are carried as error detail by JCo-origin errors.
- Added integration tests covering client initialisation, RFC execution, IDoc send, and listener scenarios.

### Fixed

- Fixed a `NullPointerException` in `ExportParameterProcessor` when an SAP export parameter was absent from the response. Null-checks are now applied before accessing parameter values.
- Fixed `IDoc listener configuration missing required repository configuration` ([ballerina-library#8722](https://github.com/ballerina-platform/ballerina-library/issues/8722)): `repositoryDestination` is now propagated from `ServerConfig` through the `Listener` to `SAPServerDataProvider`.
- Fixed `IDoc listener cascading stop error` ([ballerina-library#8723](https://github.com/ballerina-platform/ballerina-library/issues/8723)): Refactored `Listener` lifecycle to correctly sequence graceful and immediate JCo server shutdown, preventing cascading errors on stop.
- Fixed `Singleton JCo destination provider for multi-client support` ([ballerina-library#8721](https://github.com/ballerina-platform/ballerina-library/issues/8721)): Refactored `SAPDestinationDataProvider` and `SAPServerDataProvider` to thread-safe singletons with per-destination/per-server registration, enabling multiple concurrent clients and listeners without JCo provider conflicts.
