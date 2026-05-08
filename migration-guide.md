# Migrating from `sap.jco` 1.0.0 to 2.0.0

Version 2.0.0 introduces breaking changes to the `Client.execute()` signature, client and listener initialization, and error types. This guide covers every breaking change with before/after examples and a final checklist.

---

## Prerequisites

- Ballerina **2201.11.x** or later
- Add the minimum required version of `sap.jco` to your `Ballerina.toml`:

  ```toml
  [[dependency]]
  org = "ballerinax"
  name = "sap.jco"
  version = "2.0.0"
  ```

---

## Summary of breaking changes

| Area | What changed | Impact |
|---|---|---|
| `Client.execute()` | Input restructured into `RfcParameters`; return descriptor renamed `returnType` | All RFC call sites must be updated |
| `Client.init()` | Parameter renamed from `configurations` to `config`; new optional `destinationId` | Named-argument call sites break |
| Error types | Generic `Error` replaced with ten distinct error types | `is Error` checks still work; type-specific `is` checks need updating |
| `ServerConfig` | `connectionCount` added (defaulted); `repositoryDestination` added and is now **required** | Listener configs must now include `repositoryDestination` |
| `Service` type | Renamed to `IDocService`; new `RfcService` type added | All `service jco:Service` declarations must be renamed |

---

## 1. `Client.execute()` — input and output restructured

This is the most impactful change. Every RFC call site must be updated.

### What changed and why

The old signature accepted a flat record for input and discarded all table output:

```ballerina
// 1.0.0 signature
isolated remote function execute(
    string functionName,
    record {|FieldType?...;|} importParams,
    typedesc<record {|FieldType?...;|}|xml|json?> exportParams = <>
) returns exportParams|Error
```

This had two concrete bugs:
1. **Table output was silently dropped.** Export-only parameter processing meant any tabular data returned by SAP (e.g. `DATA` from `RFC_READ_TABLE`) was discarded.
2. **Table input failed at runtime.** Array fields in the flat `importParams` were routed to the wrong JCo parameter list, throwing a `JCoException`.

The new signature uses explicit input categories and merges all output into one flat response record:

```ballerina
// 2.0.0 signature
isolated remote function execute(
    string functionName,
    RfcParameters parameters = {},
    typedesc<RfcRecord|xml> returnType = <>
) returns returnType|Error
```

### Migrating `execute()` calls

**Case 1 — RFC with scalar import parameters (most common)**

```ballerina
// Before
MyResponse result = check sapClient->execute("STFC_CONNECTION", {"REQUTEXT": "hello"}, MyResponse);

// After — wrap importParams content inside importParameters
MyResponse result = check sapClient->execute("STFC_CONNECTION", {importParameters: {"REQUTEXT": "hello"}}, MyResponse);
```

**Case 2 — RFC with no input parameters**

```ballerina
// Before
MyResponse result = check sapClient->execute("PING", {}, MyResponse);

// After — parameters defaults to {}, omit entirely and use named argument for returnType
MyResponse result = check sapClient->execute("PING", returnType = MyResponse);
```

**Case 3 — RFC with table input parameters**

Previously these failed at runtime. Now use `tableParameters`:

```ballerina
// Before (runtime error — array fields were routed to the wrong JCo list)
check sapClient->execute("RFC_READ_TABLE", {
    "QUERY_TABLE": "MARA",
    "OPTIONS": [{"TEXT": "MATNR LIKE '100%'"}]
}, ReadTableResponse);

// After
type OptionsRow record {| string TEXT; |};
type DataRow record {| string WA; |};
type ReadTableResponse record {| DataRow[] DATA; |};

ReadTableResponse result = check sapClient->execute("RFC_READ_TABLE",
    {
        importParameters: {"QUERY_TABLE": "MARA", "DELIMITER": "|"},
        tableParameters: {
            OPTIONS: [{"TEXT": "MATNR LIKE '100%'"}],
            FIELDS:  [{"FIELDNAME": "MATNR"}, {"FIELDNAME": "MBRSH"}]
        }
    },
    ReadTableResponse
);
```

**Case 4 — XML response (JSON support removed, now includes table data)**

JSON return type support has been removed in 2.0.0 (`typedesc<RfcRecord|xml>`). If you were using `json` as the return type, switch to `xml` or a typed `RfcRecord`. The input wrapping is required:

```ballerina
// Before
xml response = check sapClient->execute("RFC_READ_TABLE", {"QUERY_TABLE": "MARA"});

// After
xml response = check sapClient->execute("RFC_READ_TABLE", {importParameters: {"QUERY_TABLE": "MARA"}});
```

### Output: table parameters now included

In 2.0.0 the response record is populated from both the SAP export parameter list **and** the table parameter list. Fields are matched by name; no change to your response record types is required.

```ballerina
// Response type works the same — 2.0.0 now also populates table-parameter fields
type MaterialListResponse record {|
    MaterialRow[] MATNRLIST;   // populated from table parameter list (new in 2.0.0)
    ReturnMsg[]   RETURN;      // populated from table parameter list (new in 2.0.0)
|};
```

### New types introduced

| Type | Description |
|---|---|
| `RfcRecord` | Named alias for `record {\| FieldType?...; \|}` — the base type for parameter values |
| `RfcParameters` | Wrapper with `importParameters` and `tableParameters` sections |
| `RepositoryDestination` | Union type `string\|DestinationConfig` for listener repository destination |

`boolean` has been added to `FieldType`, enabling boolean values in RFC parameters. This is not a breaking change — existing code continues to work.

---

## 2. `Client.init()` — parameter renamed; new `destinationId`

### What changed

```ballerina
// 1.0.0
public isolated function init(DestinationConfig|AdvancedConfig configurations) returns Error?

// 2.0.0
public isolated function init(DestinationConfig|AdvancedConfig config,
                              string destinationId = uuid:createType4AsString()) returns Error?
```

The parameter was renamed from `configurations` to `config`.

### Migrating `Client` initialization

**Positional call sites — no change required:**

```ballerina
// Before and after — positional argument is unaffected
sap:Client client = check new (destConfig);
```

**Named-argument call sites — rename the argument:**

```ballerina
// Before
sap:Client client = check new (configurations = destConfig);

// After
sap:Client client = check new (config = destConfig);
```

### When to use `destinationId`

Supply an explicit `destinationId` when a `Listener` needs to call your client for IDoc metadata resolution. The value must match the `repositoryDestination` field in `ServerConfig`:

```ballerina
sap:Client client = check new (destConfig, destinationId = "MY_DEST");

sap:Listener sapListener = check new ({
    gwhost: "sap-gw.example.com",
    gwserv: "3300",
    progid: "BALLERINA_PROG",
    repositoryDestination: "MY_DEST"   // must match destinationId above
});
```

When `destinationId` is omitted, a UUID is generated automatically — no change needed for clients that are not referenced by a listener.

---

## 3. Error types — generic union replaced with distinct types

### What changed

1.0.0 exposed a single `Error` type. 2.0.0 introduces ten distinct error types, all members of the `Error` union:

| 2.0.0 error type | When raised | Detail record |
|---|---|---|
| `ConnectionError` | Network / gateway unreachable | `JCoErrorDetail` |
| `LogonError` | SAP logon credentials rejected | `JCoErrorDetail` |
| `ResourceError` | Connection pool or memory exhausted | `JCoErrorDetail` |
| `SystemError` | SAP internal failure | `JCoErrorDetail` |
| `AbapApplicationError` | ABAP function module exception | `AbapApplicationErrorDetail` |
| `JCoError` | Other JCo-level failure | `JCoErrorDetail` |
| `IDocError` | IDoc XML parsing or send failure | — |
| `ParameterError` | RFC parameter type conversion failure | — |
| `ConfigurationError` | Init / lifecycle failure (e.g. `execute` after `close`) | — |
| `ExecutionError` | Unexpected runtime error | — |

### Migrating error handling

**Generic `is sap:Error` checks — no change required:**

```ballerina
// Works unchanged in 2.0.0
MyResponse|sap:Error result = sapClient->execute("FUNC", {importParameters: params}, MyResponse);
if result is sap:Error {
    log:printError("RFC failed", result);
}
```

**Add specific handling where useful:**

```ballerina
do {
     MyResponse result = check sapClient->execute("FUNC", {importParameters: params}, MyResponse);
     // process result
} on fail sap:Error err {
     if err is sap:AbapApplicationError {
         sap:AbapApplicationErrorDetail detail = err.detail();
         log:printError(string `ABAP error ${detail.abapMsgNumber ?: "?"}: ${detail.abapMsgV1 ?: ""}`, err);
     } else if err is sap:ConnectionError {
         log:printError("Cannot reach SAP gateway — check network", err);
     } else if err is sap:LogonError {
         log:printError("SAP logon rejected — check credentials", err);
     } else {
         log:printError("RFC failed", err);
     }
}
```

**`JCoErrorDetail` fields** (available on `ConnectionError`, `LogonError`, `ResourceError`, `SystemError`, `JCoError`, `AbapApplicationError`):

```ballerina
if err is sap:ConnectionError {
    sap:JCoErrorDetail detail = err.detail();
    int group = detail.errorGroup;
    string? key = detail.key;
}
```

---

## 4. `ServerConfig` — `repositoryDestination` is now required

### What changed

```ballerina
// 1.0.0
public type ServerConfig record {|
    string gwhost;
    string gwserv;
    string progid;
|};

// 2.0.0 — connectionCount defaulted; repositoryDestination is now required
public type RepositoryDestination string|DestinationConfig;

public type ServerConfig record {|
    string gwhost;
    string gwserv;
    string progid;
    int connectionCount = 2;                      // new — defaults to 2, no action required
    RepositoryDestination repositoryDestination;   // required
|};
```

`repositoryDestination` is a **required** field. All `ServerConfig` literals and `Config.toml` files must include it. It accepts two forms:

**Option 1 — Reference an existing Client destination** (value must match the `destinationId` of a `Client` initialised before the `Listener`):

```ballerina
jco:Client sapClient = check new (destConfig, destinationId = "MY_DEST");

jco:Listener sapListener = check new ({
    gwhost: "sap-gw.example.com",
    gwserv: "3300",
    progid: "BALLERINA_PROG",
    repositoryDestination: "MY_DEST"   // must match destinationId above
});
```

**Option 2 — Supply SAP credentials directly** (the listener registers an internal JCo destination automatically, so no separate `Client` is required):

```ballerina
jco:Listener sapListener = check new ({
    gwhost: "sap-gw.example.com",
    gwserv: "3300",
    progid: "BALLERINA_PROG",
    repositoryDestination: {
        ashost: "sap.example.com",
        sysnr: "00",
        jcoClient: "100",
        user: "SAP_USER",
        passwd: "SAP_PASSWORD"
    }
});
```

---

## 5. `Service` renamed to `IDocService`; new `RfcService` type

### What changed

The `Service` distinct service type has been renamed to `IDocService`. A new `RfcService` type is introduced for handling inbound RFC calls from SAP.

### Migrating listener service declarations

**Rename `jco:Service` to `jco:IDocService` everywhere:**

```ballerina
// Before
service jco:Service on iDocListener {
    remote function onReceive(xml iDoc) returns error? { ... }
    remote function onError(error 'error) returns error? { ... }
}

// After
service jco:IDocService on iDocListener {
    remote function onReceive(xml iDoc) returns error? { ... }
    remote function onError(error err) returns error? { ... }
}
```

Note also that the `onError` parameter was renamed from `'error` to `err`.

**`onError` scope:** `onError` now fires only for framework faults — JCo gateway/server errors, pre-dispatch failures (RFC parameter construction, IDoc XML rendering), and post-dispatch failures (RFC response serialization). Errors returned or thrown from `onCall`/`onReceive` are **not** routed through `onError`: `onCall` errors surface to SAP as `AbapException`, and `onReceive` errors are logged.

### Using the new `RfcService` type

Attach an `RfcService` to handle inbound RFC calls from SAP. At most one `IDocService` and one `RfcService` may be attached to the same listener simultaneously.

```ballerina
service jco:RfcService on rfcListener {
    remote function onCall(string functionName, jco:RfcParameters parameters) returns jco:RfcRecord|xml|error? {
        io:println("RFC called: ", functionName);
        return ();
    }
    remote function onError(error err) returns error? {
        io:println("Error: ", err.message());
    }
}
```

---

## 6. `Client.sendIDoc()` — new optional parameters; qRFC now functional

This is not a breaking change — existing `sendIDoc` calls continue to work. Two optional parameters have been added and a pre-existing qRFC bug has been fixed.

### New parameters

```ballerina
// 1.0.0 / 2.0.0 before this fix
isolated remote function sendIDoc(xml iDoc, IDocType iDocType = DEFAULT) returns Error?

// 2.0.0 with qRFC fix
isolated remote function sendIDoc(xml iDoc, IDocType iDocType = DEFAULT,
                                  string? tid = (), string? queueName = ()) returns Error?
```

| Parameter | Default | Purpose |
|---|---|---|
| `tid` | auto-generated | Supply your own 24-hex Transaction ID for end-to-end idempotency. Useful when you persist the outbound intent (outbox pattern) and need SAP to recognise the TID as already processed on retry. |
| `queueName` | `()` | Required for qRFC types (`VERSION_3_IN_QUEUE`, `VERSION_3_IN_QUEUE_VIA_QRFC`). Names the SAP inbound queue. Ignored (with a warning) for tRFC types. |

### qRFC bug fix

In earlier builds, `VERSION_3_IN_QUEUE` ("Q") and `VERSION_3_IN_QUEUE_VIA_QRFC` ("I") were declared in the `IDocType` enum but not actually supported. The Java implementation always called the 4-arg `JCoIDoc.send` with no queue name, which fails at the SAP JCo layer for qRFC versions. These types now work correctly.

### Migrating existing `sendIDoc` calls

No migration required for existing calls. The new parameters are optional with backward-compatible defaults.

**To send via qRFC (ordered delivery):**

```ballerina
// Ordered send into a named inbound queue.
// All IDocs in this queue are processed by SAP in FIFO order.
check sapClient->sendIDoc(iDoc, iDocType = jco:VERSION_3_IN_QUEUE_VIA_QRFC,
                          queueName = "MATMAS_SENDER_CLNT100");
```

**To supply your own TID for idempotency:**

SAP TIDs must be exactly 24 uppercase hexadecimal characters. Derive a stable TID from your
database row ID (e.g. by hashing) and **persist `sapTid` before calling `sendIDoc`** so that
retries reuse the identical TID. SAP uses ARFCRSTATE to detect already-processed TIDs.

```ballerina
import ballerina/crypto;

// Derive a 24-char hex TID from the database row ID.
// Persist sapTid alongside outboxRowId before calling sendIDoc so retries use the same TID.
byte[] hash = crypto:hashSha256(outboxRowId.toBytes());
string sapTid = hash.toBase16().toUpperAscii().substring(0, 24);

check sapClient->sendIDoc(iDoc, tid = sapTid);
```

---

## 7. `Client.close()` — new lifecycle method

`close()` is a new addition, not a breaking change. It releases the JCo destination registration. After `close()`, calls to `execute()` or `sendIDoc()` return a `ConfigurationError`. Calling `close()` more than once is safe.

```ballerina
// Best practice: close the client when done
check sapClient.close();
```

---

## Migration checklist

Work through each item in order:

- [ ] Update the minimum required Ballerina version and `sap.jco` version in your `Ballerina.toml`
- [ ] **`execute()` — wrap import params:** change `{"KEY": val}` to `{importParameters: {"KEY": val}}`
- [ ] **`execute()` — move array-typed fields:** move any array-valued fields from `importParams` to `tableParameters: {"PARAM_NAME": [...]}`
- [ ] **`execute()` — rename the typedesc argument:** change `exportParams = MyType` to `returnType = MyType`
- [ ] **`execute()` — empty params:** change `execute("FUNC", {}, MyType)` to `execute("FUNC", returnType = MyType)`
- [ ] **`execute()` — response types:** add table-parameter fields to response record types if you need tabular data that was previously unavailable
- [ ] **`Client.init()` named args:** rename `configurations =` to `config =` at any named call sites
- [ ] **`Client.init()` destinationId:** supply an explicit `destinationId` for any client referenced by a listener's `repositoryDestination`
- [ ] **`ServerConfig`:** add `repositoryDestination` (now required) — either a `string` matching the `destinationId` of an already-initialised `Client`, or an inline `DestinationConfig`
- [ ] **`Service` → `IDocService`:** rename all `service jco:Service` declarations to `service jco:IDocService`
- [ ] **`onError` parameter:** rename `error 'error` to `error err` in all `onError` remote function signatures
- [ ] **Error handling:** review `on fail` / `is` checks — `is sap:Error` continues to work; add specific error types where finer handling is needed
- [ ] **`Client.close()`:** add `close()` calls in cleanup paths where clients are no longer needed
- [ ] **`sendIDoc` qRFC:** if you were using `VERSION_3_IN_QUEUE` or `VERSION_3_IN_QUEUE_VIA_QRFC`, add the required `queueName` argument — previously these calls failed at runtime; they now require a queue name
- [ ] Run the integration test suite against your SAP sandbox to confirm correct behavior
