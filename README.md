# ISO8583 PG Simulator

Spring Boot + Netty based ISO 8583 simulator with:
- `SERVER` mode (accepts ISO TCP traffic)
- `CLIENT` mode (sends ISO TCP traffic to server instance)
- MTI profile/rule/scenario driven behavior
- Optional NMM lifecycle (LOGON/ECHO/LOGOFF)

## Prerequisites

For Docker setup:
- Docker Desktop (Mac/Windows) or Docker Engine (Linux)
- Docker Compose plugin (`docker compose`)

For local Maven setup:
- Java 17+
- Maven wrapper is included (`./mvnw`)

## Setup From Scratch (Recommended: Docker Compose)

1) Clone and enter repo

```bash
git clone <your-repo-url>
cd pgsim_ISO8583-mainV3
```

2) Build and start both SERVER and CLIENT

```bash
docker compose -f docker.compose.yml up --build -d
```

3) Verify both instances

```bash
curl -s http://127.0.0.1:8081/actuator/health
curl -s http://127.0.0.1:8082/actuator/health
```

4) Open UI
- Server UI: `http://127.0.0.1:8081/index.html`
- Client UI: `http://127.0.0.1:8082/index.html`

5) Stop everything

```bash
docker compose -f docker.compose.yml down
```

## Setup with Dockerfile (Manual Containers)

1) Build image

```bash
docker build -t pgsim:latest .
```

2) Create network

```bash
docker network create pgsim-net
```

3) Start SERVER

```bash
docker run -d --name pgsim-server \
  --network pgsim-net \
  -p 8081:8081 \
  -p 8080:8080 \
  -e APP_ARGS="--server.port=8081 --simulator.mode=SERVER --simulator.instance.role=SERVER --simulator.mode.switch-enabled=false --simulator.tcp.port=8080" \
  pgsim:latest
```

4) Start CLIENT

```bash
docker run -d --name pgsim-client \
  --network pgsim-net \
  -p 8082:8082 \
  -e APP_ARGS="--server.port=8082 --simulator.mode=CLIENT --simulator.instance.role=CLIENT --simulator.mode.switch-enabled=false --simulator.client.host=pgsim-server --simulator.client.port=8080 --simulator.remote.server.host=pgsim-server --simulator.remote.server.port=8081 --pgsim.nmm.enabled=true --pgsim.nmm.echo-interval-ms=60000 --pgsim.nmm.retry-count=5 --pgsim.nmm.retry-delay-ms=10000 --pgsim.nmm.auto-reconnect=true --pgsim.nmm.response-timeout-ms=10000" \
  pgsim:latest
```

5) Verify

```bash
curl -s http://127.0.0.1:8081/actuator/health
curl -s http://127.0.0.1:8082/actuator/health
docker ps
```

6) Cleanup

```bash
docker rm -f pgsim-client pgsim-server
docker network rm pgsim-net
```

## Run Locally Without Docker (Maven)

Start SERVER:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8081 --simulator.mode=SERVER --simulator.instance.role=SERVER --simulator.mode.switch-enabled=false --simulator.tcp.port=8080"
```

Start CLIENT (second terminal):

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8082 --simulator.mode=CLIENT --simulator.instance.role=CLIENT --simulator.mode.switch-enabled=false --simulator.client.host=127.0.0.1 --simulator.client.port=8080 --simulator.remote.server.host=127.0.0.1 --simulator.remote.server.port=8081 --pgsim.nmm.enabled=true --pgsim.nmm.echo-interval-ms=60000 --pgsim.nmm.retry-count=5 --pgsim.nmm.retry-delay-ms=10000 --pgsim.nmm.auto-reconnect=true --pgsim.nmm.response-timeout-ms=10000"
```

## Quick Functional Check

Send from CLIENT instance:

```bash
curl -sS -X POST http://127.0.0.1:8082/api/simulator/send \
  -H "Content-Type: application/json" \
  -d '{"mti":"0100","fields":{"2":"5123456789012345","3":"000000","4":"000000001000","11":"123456"}}'
```

Expected:
- response MTI: `0110`
- response code `DE39`: `00` (or configured rule code)

## Notes

- `docker.compose.yml` is intentionally named with dot notation; use `-f docker.compose.yml`.
- Runtime defaults are in `src/main/resources/application.properties`.
- MTI definitions are in `src/main/resources/message-config.json`.

## Troubleshooting

- Port already in use:
  - change host port mappings in `docker.compose.yml` or stop existing process
- Container healthy but no UI:
  - check logs: `docker logs pgsim-server` / `docker logs pgsim-client`
- Rule/bitmap changes not visible:
  - use UI Refresh/Sync in profile/config pages
  - verify via `GET /api/config/{mti}`
# ISO8583 PG Simulator


🟢 Quick Start (1 command)
git clone <your-repo-url>
cd <repo>
docker compose up --build

Open:

Server → http://localhost:8081
Client → http://localhost:8082

## Quick Start with Docker (Recommended)

This project can be built and run directly from the included `Dockerfile` (no local Java/Maven install required).

### 1) Build the image

```bash
docker build -t pgsim:latest .
```

### 2) Create a Docker network

```bash
docker network create pgsim-net
```

### 3) Run SERVER instance

```bash
docker run -d --name pgsim-server \
  --network pgsim-net \
  -p 8081:8081 \
  -p 8080:8080 \
  -e APP_ARGS="--server.port=8081 --simulator.mode=SERVER --simulator.instance.role=SERVER --simulator.mode.switch-enabled=false --simulator.tcp.port=8080" \
  pgsim:latest
```

### 4) Run CLIENT instance

```bash
docker run -d --name pgsim-client \
  --network pgsim-net \
  -p 8082:8082 \
  -e APP_ARGS="--server.port=8082 --simulator.mode=CLIENT --simulator.instance.role=CLIENT --simulator.mode.switch-enabled=false --simulator.client.host=pgsim-server --simulator.client.port=8080 --simulator.remote.server.host=pgsim-server --simulator.remote.server.port=8081 --pgsim.nmm.enabled=true --pgsim.nmm.echo-interval-ms=60000 --pgsim.nmm.retry-count=5 --pgsim.nmm.retry-delay-ms=10000 --pgsim.nmm.auto-reconnect=true --pgsim.nmm.response-timeout-ms=10000" \
  pgsim:latest
```

### 5) Verify

```bash
curl -s http://127.0.0.1:8081/actuator/health
curl -s http://127.0.0.1:8082/actuator/health
```

Open UIs:
- Server UI: `http://127.0.0.1:8081/index.html`
- Client UI: `http://127.0.0.1:8082/index.html`

### 6) Stop and clean up

```bash
docker rm -f pgsim-client pgsim-server
docker network rm pgsim-net
```

---

## Run as separate SERVER and CLIENT processes (Local Maven)

Use the same codebase and start two different processes with startup configuration.

### 1) Start SERVER instance

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8081 --simulator.mode=SERVER --simulator.instance.role=SERVER --simulator.mode.switch-enabled=false --simulator.tcp.port=8080"
```

### 2) Start CLIENT instance

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8082 --simulator.mode=CLIENT --simulator.instance.role=CLIENT --simulator.mode.switch-enabled=false --simulator.client.host=127.0.0.1 --simulator.client.port=8080"
```

With this setup:
- each process role is chosen at startup,
- roles are shown separately in the frontend,
- runtime role switching is disabled for stable deployment behavior.

python3 src/main/java/com/payu/pgsim/test_client.py
./mvnw spring-boot:run

#test

MTI: 0100
Response MTI: 0110
👉 Save Config

✅ Request Bitmap (tick these ONLY)
2
3
4
11
✅ Response Bitmap (tick these)
7
11
12
37
38
39
⬜ Secondary Bitmap
UNCHECKED
👉 Save Bitmap

Now add fields one by one:

🔹 Field 2 (PAN)
Field Number: 2
Type: PAN
Mandatory: ✔
Value: (leave empty)
Length: 16

👉 Click Add Field

🔹 Field 3
Field Number: 3
Type: NUMERIC
Mandatory: ✔
Value: 000000
Length: 6

👉 Add Field

🔹 Field 4
Field Number: 4
Type: NUMERIC
Mandatory: ✔
Value: (leave empty)
Length: 12

👉 Add Field

🔹 Field 11
Field Number: 11
Type: NUMERIC
Mandatory: ✔
Value: (leave empty)
Length: 6

👉 Add Field

🔹 RESPONSE FIELDS (IMPORTANT)

Now add:

Field 7
Field Number: 7
Type: DATETIME
Value: ${DATETIME}
Length: 10

Field 12
Field Number: 12
Type: NUMERIC
Value: ${TIME}
Length: 6

Field 37
Field Number: 37
Type: ALPHA
Value: ${RRN}
Length: 12

Field 38
Field Number: 38
Type: ALPHA
Value: AUTH01
Length: 6

Field 39
Field Number: 39
Type: NUMERIC
Value: 00
Length: 2

👉 Click Save Field

🟥 4. RULE CONFIGURATION

Add rule:

Field: 4
Operator: >
Value: 10000
Response Code: 51

👉 Click:

Add Rule
Save Rule

🟪 5. SCENARIO CONFIGURATION
Type: DELAY
Delay: 1000

👉 Save Scenario

🟫 6. TRANSACTION BUILDER
Fill:
MTI: 0100
Add fields:
Field 2 - 5123456789012345
Field 3 - 000000
Field 4 - 000000001000
Field 11 - 123456

👉 Click:
Send Transaction

🧪 EXPECTED RESULT (SUCCESS)
MTI: 0110
DE39: 00
-----------------------------------------------------------------------------------------

## Manual test instructions (same format)

🟦 1. START SERVER AND CLIENT

Run SERVER:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8081 --simulator.mode=SERVER --simulator.instance.role=SERVER --simulator.mode.switch-enabled=false --simulator.tcp.port=8080"
```

Run CLIENT:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8082 --simulator.mode=CLIENT --simulator.instance.role=CLIENT --simulator.mode.switch-enabled=false --simulator.client.host=127.0.0.1 --simulator.client.port=8080 --simulator.remote.server.host=127.0.0.1 --simulator.remote.server.port=8081"
```

✅ Make sure both are UP:
- `http://127.0.0.1:8081/actuator/health`
- `http://127.0.0.1:8082/actuator/health`

🟨 2. USE PROFILE-BASED CONFIG FOR ALL REQUEST MTIs

Configure these MTI pairs in UI (or keep default `message-config.json`):

- `0100 -> 0110`
- `0200 -> 0210`
- `0400 -> 0410`
- `0420 -> 0430`
- `0800 -> 0810`
- `0820 -> 0830`

Bitmap guidance:
- `0100`: request bits `2,3,4,11`; response bits `7,11,12,37,38,39`
- `0200`: request bits `2,3,4,7,11`; response bits `7,11,12,37,38,39`
- `0400`: request bits `11`; response bits `11,39`
- `0420`: request bits `11`; response bits `11,39`
- `0800`: request bits `7,11,70`; response bits `7,11,12,39,70`
- `0820`: request bits `7,11,70`; response bits `7,11,12,39,70`

🟪 3. OPTIONAL CHECK (CLIENT FIELD VIEW)

```bash
curl -s "http://127.0.0.1:8082/api/profiles/client-fields/0100"
```

Expected: mandatory bits include `2,3,4,11`.

🟫 4. SEND TRANSACTIONS FROM CLIENT (MANUAL TEST CASES)

Use endpoint:

`POST http://127.0.0.1:8082/api/simulator/send`

For DE7, generate value from:

```bash
date +%m%d%H%M%S
```

🔹 Test Case A: `0800 -> 0810`

Request body:

```json
{"mti":"0800","fields":{"7":"DE7_HERE","11":"200001","70":"001"}}
```

Expected:
- Response MTI = `0810`
- `DE39 = 00`

🔹 Test Case B: `0100 -> 0110` (approval path)

Request body:

```json
{"mti":"0100","fields":{"2":"5123456789012345","3":"000000","4":"000000001000","11":"200010"}}
```

Expected:
- Response MTI = `0110`
- `DE39 = 00`

🔹 Test Case C: `0200 -> 0210`

Request body:

```json
{"mti":"0200","fields":{"2":"5123456789012345","3":"000000","4":"000000001000","7":"DE7_HERE","11":"200020"}}
```

Expected:
- Response MTI = `0210`
- `DE39 = 00`

🔹 Test Case D: `0400 -> 0410`

Request body:

```json
{"mti":"0400","fields":{"11":"200020"}}
```

Expected:
- Response MTI = `0410`
- `DE39 = 00`

🔹 Test Case E: `0420 -> 0430`

Request body:

```json
{"mti":"0420","fields":{"11":"200030"}}
```

Expected:
- Response MTI = `0430`
- `DE39 = 00`

🔹 Test Case F: `0820 -> 0830`

Request body:

```json
{"mti":"0820","fields":{"7":"DE7_HERE","11":"200040","70":"001"}}
```

Expected:
- Response MTI = `0830`
- `DE39 = 00`

🟥 5. NEGATIVE TEST (MANDATORY FIELD MISSING)

Send `0100` without DE4:

```json
{"mti":"0100","fields":{"2":"5123456789012345","3":"000000","11":"200099"}}
```

Expected:
- HTTP `400`
- `"error":"VALIDATION_ERROR"`
- message mentions missing mandatory DE4

🟧 6. RULE DECLINE TEST

Send `0100` with high amount:

```json
{"mti":"0100","fields":{"2":"5123456789012345","3":"000000","4":"000000050000","11":"200051"}}
```

Expected:
- Response MTI = `0110`
- `DE39 = 51`

🟩 7. NETWORK ECHO TEST (`0800`, DE70=301)

Send only allowed fields:

```json
{"mti":"0800","fields":{"7":"DE7_HERE","11":"200060","70":"301"}}
```

Expected:
- Response MTI = `0810`
- `DE39 = 00`

⚠️ Do not add extra fields (example DE48) unless profile allows them; server may respond with `DE39=96`.
