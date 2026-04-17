# Changelog

This file contains all the notable changes done to the Ballerina `sap.jco` package through the releases.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Added `destinationId` parameter to the `Client.init` function, allowing an explicit name to be assigned to the RFC destination. This is required when a `Listener` references the client as its `repositoryDestination`.
- Added `close()` method to `Client` to release the JCo destination registration. After `close`, calls to `execute` or `sendIDoc` return a `ConfigurationError`. Calling `close` more than once is safe.
- Added `connectionCount` field to `ServerConfig` to control the maximum number of concurrent JCo server connections (maps to `jco.server.connection_count`, default `2`).
- Added required `repositoryDestination` field (typed as `RepositoryDestination`) to `ServerConfig` to specify the RFC destination used by the JCo server to look up IDoc and RFC function module metadata. Accepts either a `string` matching the `destinationId` of an already-initialised `Client`, or a `DestinationConfig` that the listener registers as an internal JCo destination automatically.
- Added `boolean` to `FieldType`, enabling boolean values in RFC import, export, and table parameters.
- Added `RfcRecord` type alias (`record {| FieldType?...; |}`) as the base record type for RFC import, export, and table row values.
- Added `RfcParameters` record type that wraps `importParameters` (`RfcRecord`) and `tableParameters` (`map<RfcRecord[]>`) for use with `execute`.
- [Changed `execute` signature: import and table parameters are now supplied via `RfcParameters parameters = {}`; the return type descriptor parameter is renamed to `returnType` and typed as `typedesc<RfcRecord|xml>` (`json` support removed); the response now merges both export parameters and table parameters returned by SAP.](https://github.com/ballerina-platform/ballerina-library/issues/8714)
- Introduced distinct error types aligned with Ballerina conventions: `ConnectionError`, `LogonError`, `ResourceError`, `SystemError`, `AbapApplicationError`, `JCoError`, `IDocError`, `ParameterError`, `ConfigurationError`, and `ExecutionError`. All are members of the existing `Error` union.
- Added `JCoErrorDetail` and `AbapApplicationErrorDetail` record types that are carried as error detail by JCo-origin errors.
- Added `IDocService` distinct service type for receiving IDocs from the SAP system. Replaces the previous `Service` type.
- Added `RfcService` distinct service type for handling inbound RFC calls from the SAP system. Exposes `onCall(string functionName, RfcParameters parameters)` and `onError(error err)` remote functions. The return value of `onCall` is serialized and sent back to the SAP caller.
- Updated `Listener.attach` and `Listener.detach` to accept `IDocService|RfcService`. At most one `IDocService` and one `RfcService` may be attached simultaneously to the same listener.
- Gateway connectivity errors (unreachable gateway, JCo internal failures) are now dispatched to the attached service's `onError` handler as `ExecutionError`. JCo retries automatically; no listener restart is needed.
- Added integration tests covering client initialisation, RFC execution, IDoc send, and listener scenarios (IDoc and RFC service types).

### Changed

- **Breaking:** Renamed `Service` type to `IDocService`. Update all `service jco:Service` declarations to `service jco:IDocService`.
- **Breaking:** `repositoryDestination` in `ServerConfig` is a required field. All listener configurations must supply a `repositoryDestination` matching the `destinationId` of an already-initialised `Client`.
- Renamed `onError` parameter from `'error` to `err` in both `IDocService` and `RfcService`.
- Gateway and JCo server errors are now dispatched to the attached service's `onError` handler as `ExecutionError`. Previously `BallerinaThrowableListener` only logged these failures and `onError` was never invoked for them. `Listener.'start()` remains non-blocking (JCo's internal connection threads do the gateway handshake); pre-flight failures (listener not initialised, already started) are still returned synchronously.

### Fixed

- Fixed a `NullPointerException` in `ExportParameterProcessor` when an SAP export parameter was absent from the response. Null-checks are now applied before accessing parameter values.
- Fixed `IDoc listener configuration missing required repository configuration` ([ballerina-library#8722](https://github.com/ballerina-platform/ballerina-library/issues/8722)): `repositoryDestination` is now propagated from `ServerConfig` through the `Listener` to `SAPServerDataProvider`.
- Fixed `IDoc listener cascading stop error` ([ballerina-library#8723](https://github.com/ballerina-platform/ballerina-library/issues/8723)): Refactored `Listener` lifecycle to correctly sequence graceful and immediate JCo server shutdown, preventing cascading errors on stop.
- Fixed `Singleton JCo destination provider for multi-client support` ([ballerina-library#8721](https://github.com/ballerina-platform/ballerina-library/issues/8721)): Refactored `SAPDestinationDataProvider` and `SAPServerDataProvider` to thread-safe singletons with per-destination/per-server registration, enabling multiple concurrent clients and listeners without JCo provider conflicts.
