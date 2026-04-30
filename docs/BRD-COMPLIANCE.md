# Wibmo ISO 8583 Simulator — BRD alignment notes

This document maps implementation choices to common BRD themes (JSON schema, FR-2 encoding, TR-6 TLS, observability, ops). It is not a substitute for the official BRD PDF.

## §10 — BRD-identical JSON (`/api/brd/v1`)

- **Shape:** `BrdMessageTypeConfig` uses nested `requestConfig` and `responseConfig` with `mandatoryBits`, `optionalBits`, `fieldConfigs`, `responseBits`, `defaultResponseCode`, and optional `requestConfig.secondaryBitmap`.
- **Field objects:** `valueType` accepts BRD **`mode`** as a JSON alias. **`conditionalValues`** entries accept **`condition`** with aliases **`when`** and **`expression`**.
- **OpenAPI:** Available at `/v3/api-docs` (Swagger UI `/swagger-ui.html`).

## FR-2 — ASCII + binary paths

- **Primary packager:** `pgsim.iso.primary-packager` (default `iso87ascii.xml`) — used to **pack** outbound messages and as the first **unpack** attempt for inbound traffic.
- **Secondary packager:** `pgsim.iso.secondary-packager` (default `iso87binary.xml`) — used only if the primary unpack throws (binary wire format / IFB_* layout).
- **REST simulator:** Set `hexFieldValues: true` on `SimulatorRequest` to supply **hex-encoded** field payloads (decoded to bytes before `ISOMsg.set`).

## TR-6 — TLS on ISO TCP

- **Enable:** `pgsim.tcp.tls.enabled=true` and set `pgsim.tcp.tls.keystore` to a PKCS12 or JKS file (password via `pgsim.tcp.tls.keystore-password`).
- **Stub:** If TLS is enabled but the keystore is missing or invalid, the process **continues on plain TCP** and logs a TR-6 stub warning (suitable for labs; not for production).

## §9.2.4 — Bitmap analysis

- After a successful inbound parse, **`BitMapAnalyzer`** drives an INFO log line listing **present DE numbers** (architecture parity with a bitmap analysis component).

## Prometheus (§13-style metrics)

- **`/actuator/prometheus`** is exposed when `micrometer-registry-prometheus` is on the classpath and `management.endpoints.web.exposure.include` contains `prometheus`.

## Default ports (BRD vs HTTP)

- **BRD nominal ISO TCP:** `8080` → `simulator.tcp.port`.
- **HTTP UI / REST:** `8081` → `server.port` (avoids binding HTTP and ISO on the same port). Override either property as needed.

## Runtime config persistence

- **`pgsim.config.persistence.enabled=true`** writes the full MTI list to `pgsim.config.persistence.path` after mutations (`ConfigManager` updates, BRD upsert, bitmap/field/rule changes, reload).
- On startup: classpath **`message-config.json`** loads first; if persistence is enabled and the file exists, it **replaces** the in-memory configuration.

## §12 / TR-5 — automated tests

- Unit / slice tests cover BRD controllers, bitmap mandatory/optional behavior, config mapping, and a **performance smoke** test (bitmap validation throughput).
- **TR-5:** Treat the smoke test as a **regression guard**, not a formal benchmark; run JMeter/Gatling against your target QPS for evidence.

## §13.3 — Ops samples

- **`deploy/kubernetes/`** — example Deployment + Service (adjust image, probes, secrets).
- **`deploy/systemd/`** — example unit file (adjust `WorkingDirectory`, `ExecStart`, user).
