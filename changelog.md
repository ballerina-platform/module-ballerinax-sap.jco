# Changelog

This file contains all the notable changes done to the Ballerina `sap.jco` package through the releases.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.1.0] - 2026-08-25

### Added

- Added transactional RFC support, so an RFC call can be delivered with an exactly-once guarantee instead of the at-most-once guarantee of `execute`.
  - `Client.sendTRfc` calls a function module as a transactional RFC (tRFC). The call is asynchronous, so only the Transaction ID (TID) is returned.
  - `Client.sendQRfc` calls a function module as a queued RFC (qRFC). Calls placed on the same inbound queue are executed exactly once and in the order they were sent.
  - `Client.createTid` and `Client.confirmTid` expose the TID lifecycle, so a caller can obtain a TID before the first send, retry under it, and confirm it once the send succeeds. A TID may also be supplied by the caller, which allows an application idempotency key to be used as the TID.
  - `Client.sendBgRfcUnit` commits several function calls to the SAP system as a single background RFC (bgRFC) unit of work, applied together or not at all. Supplying `queueNames` makes the unit type Q; otherwise it is type T.
  - `Client.getBgRfcUnitState` and `Client.confirmBgRfcUnit` follow a committed unit through its lifecycle and release its status record in the SAP system.
- Added the `RemoteFunctionCall`, `BgRfcUnitConfig`, and `BgRfcUnitInfo` records and the `BgRfcUnitType` and `BgRfcUnitState` enums, which describe a bgRFC unit of work and its processing state.
- Added the `TransactionError` error type, whose detail carries the `tid` or `unitId` the failed call was using, so that a retry can preserve the exactly-once guarantee.

## [2.0.1] - 2026-06-17

### Fixed

- [Fix unsupported operation error when observability is enabled](https://github.com/ballerina-platform/ballerina-library/issues/8827)

## [2.0.0] - 2026-05-11

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
- Introduced distinct error types aligned with Ballerina conventions: `ConnectionError`, `LogonError`, `ResourceError`, `SystemError`, `AbapApplicationError`, `JCoError`, `IDocError`, `ParameterError`, `ConfigurationError`, and `ExecutionError`. All are members of the existing `Error` union.
- Added `JCoErrorDetail` and `AbapApplicationErrorDetail` record types that are carried as error detail by JCo-origin errors.
- Added `IDocService` distinct service type for receiving IDocs from the SAP system. Replaces the previous `Service` type.
- Added `RfcService` distinct service type for handling inbound RFC calls from the SAP system. Exposes `onCall(string functionName, RfcParameters parameters)` and `onError(error err)` remote functions. The return value of `onCall` is serialized and sent back to the SAP caller.
- Added `xml` as a supported return type for `RfcService.onCall`. The root element is ignored; each direct child element whose name matches a SAP export parameter is written as a string (JCo coerces to the target type). Table parameters must wrap rows in `<row>` child elements: `<TABLE_NAME><row><FIELD>value</FIELD></row></TABLE_NAME>`.
- Updated `Listener.attach` and `Listener.detach` to accept `IDocService|RfcService`. At most one `IDocService` and one `RfcService` may be attached simultaneously to the same listener.
- Gateway connectivity errors (unreachable gateway, JCo internal failures) are now dispatched to the attached service's `onError` handler as `ExecutionError`. JCo retries automatically; no listener restart is needed.
- Added integration tests covering client initialisation, RFC execution, IDoc send, and listener scenarios (IDoc and RFC service types).

### Changed

- **Breaking:** [Changed `execute` signature: import and table parameters are now supplied via `RfcParameters parameters = {}`; the return type descriptor parameter is renamed to `returnType` and typed as `typedesc<RfcRecord|xml>` (`json` support removed); the response now merges both export parameters and table parameters returned by SAP.](https://github.com/ballerina-platform/ballerina-library/issues/8714)
- **Breaking:** Renamed `Service` type to `IDocService`. Update all `service jco:Service` declarations to `service jco:IDocService`.
- **Breaking:** `repositoryDestination` in `ServerConfig` is a required field. It may be provided either as a string `destinationId` matching the `destinationId` of an already-initialised `Client`, or as an inline `DestinationConfig` object (in which case the connector initialises the destination automatically and no separate `Client` creation is required).
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
