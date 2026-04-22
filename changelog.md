# Changelog

This file contains all the notable changes done to the Ballerina `sap.jco` package through the releases.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Added optional `tid` and `queueName` parameters to `Client.sendIDoc`.
  - `queueName` enables qRFC sends — required for `VERSION_3_IN_QUEUE` and
    `VERSION_3_IN_QUEUE_VIA_QRFC`; previously these enum values would fail at runtime
    because the 4-arg `JCoIDoc.send` was always used with no queue.
  - `tid` lets callers supply their own Transaction ID for end-to-end idempotency
    when an outbox row ID or other persistent identifier is available; otherwise
    a TID is generated via the JCo destination as before.
  - Passing `queueName` with a tRFC `iDocType` (`DEFAULT`, `VERSION_2`, `VERSION_3`)
    logs a warning and proceeds as tRFC, ignoring the queue name.
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
- Narrowed the scope of `onError` on `IDocService` and `RfcService` to framework-level failures only: gateway/JCo errors, pre-dispatch failures (RFC parameter construction, IDoc XML rendering), and post-dispatch failures (RFC response serialization). Errors returned or thrown from `onCall`/`onReceive` are no longer routed through `onError` — `onCall` errors propagate to SAP as `AbapException`, and `onReceive` errors are logged.

### Fixed

- Fixed type mismatch in `execute` response binding: when the JCo type of an SAP export or table
  parameter does not match the declared Ballerina field type (e.g. `int FIELD` when SAP returns a
  string, or `string FIELD` when SAP returns a nested structure), the connector now returns a
  descriptive `ParameterError` such as `"Type mismatch for field 'FIELD': SAP returned string but
  declared type is int"` instead of a runtime `ClassCastException` from the Ballerina lang library.
  The fix also covers nested structures and table rows processed by `populateRecord` and
  `populateRecordArray`, which previously had unguarded casts that propagated as an opaque
  `ExecutionError`.
- Fixed missing field validation in `execute` response binding: required fields declared in the
  return type that are absent from the SAP export parameter list now produce a `ParameterError`
  (`"Required field 'X' was not found in the SAP response"`); nilable fields (`string? FIELD`)
  absent from the SAP response are set to `nil`; optional fields (`string FIELD?`) absent from the
  SAP response are silently skipped. Previously, all three cases silently left the record field at
  its Ballerina zero value.
- Fixed `Client.sendIDoc` to honour qRFC `IDocType` values. `VERSION_3_IN_QUEUE` and
  `VERSION_3_IN_QUEUE_VIA_QRFC` now route through `JCoIDoc.send(..., tid, queueName)`;
  previously the queue-less 4-arg variant was always called, causing qRFC sends to fail at runtime.
- Fixed a `NullPointerException` in `ExportParameterProcessor` when an SAP export parameter was absent from the response. Null-checks are now applied before accessing parameter values.
- Fixed `IDoc listener configuration missing required repository configuration` ([ballerina-library#8722](https://github.com/ballerina-platform/ballerina-library/issues/8722)): `repositoryDestination` is now propagated from `ServerConfig` through the `Listener` to `SAPServerDataProvider`.
- Fixed `IDoc listener cascading stop error` ([ballerina-library#8723](https://github.com/ballerina-platform/ballerina-library/issues/8723)): Refactored `Listener` lifecycle to correctly sequence graceful and immediate JCo server shutdown, preventing cascading errors on stop.
- Fixed `Singleton JCo destination provider for multi-client support` ([ballerina-library#8721](https://github.com/ballerina-platform/ballerina-library/issues/8721)): Refactored `SAPDestinationDataProvider` and `SAPServerDataProvider` to thread-safe singletons with per-destination/per-server registration, enabling multiple concurrent clients and listeners without JCo provider conflicts.
