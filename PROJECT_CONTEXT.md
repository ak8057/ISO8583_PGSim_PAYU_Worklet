# Project Context - pgsim

Last Updated: 2026-03-25
Project Root: /Users/abhaykumar/Downloads/pgsim

## 1. What This Project Is

pgsim is an ISO 8583 simulator built with Spring Boot + Netty + jPOS.

It supports two usage modes:

1. Raw TCP ISO 8583 simulation on port 9000 (2-byte length-prefixed framing)
2. REST-driven simulation and configuration UI on port 8080

Primary goal:

- Accept ISO 8583 requests
- Validate against configured bitmap and field definitions per MTI
- Generate a response MTI + response fields + response code using rules/scenarios
- Log each transaction in memory for monitoring

## 2. Runtime and Stack

- Java: 17
- Spring Boot: 3.5.11
- Build: Maven Wrapper (./mvnw)
- TCP networking: Netty 4.1.100.Final
- ISO parsing/packing: jPOS 2.1.9 using iso87ascii.xml
- Serialization: Jackson
- Boilerplate reduction: Lombok

Notable pom.xml detail:

- spring-boot-starter-web and lombok appear more than once in dependencies
- Functionality works, but dependency cleanup is recommended

## 3. Fast Start (macOS)

From project root:

```bash
./mvnw clean test
./mvnw spring-boot:run
```

Open UI:

- http://localhost:8080/index.html

Run TCP sample client:

```bash
python3 src/main/java/com/payu/pgsim/test_client.py
```

Ports:

- HTTP: 8080
- TCP ISO: 9000

## 4. Core Configuration Sources

1. src/main/resources/application.properties
   - spring.application.name=pgsim
   - simulator.tcp.port=9000
   - simulator.timeout=30000 (currently unused)
   - server.port=8080
   - pgsim.connection.timeout=30000 (ms, converted to seconds in pipeline)

2. src/main/resources/iso87ascii.xml
   - jPOS GenericPackager definition (field structure/encoding)

3. src/main/resources/message-config.json
   - Boot-time MTI behavior definitions
   - Current MTIs: 0100, 0200, 0400, 0420, 0800, 0820

Important behavior:

- ConfigManager loads message-config.json at startup
- Runtime edits are in-memory only
- POST /api/config/reload discards runtime edits and reloads from file

## 5. Request Processing Flows

### 5.1 TCP Flow

TcpServer -> TcpServerInitializer -> TcpServerHandler -> MessageHandler.processTcp

Technical details:

- Netty frame decoder expects 2-byte length prefix
- TcpServerHandler offloads processing to a fixed thread pool (ExecutorService)
- For TIMEOUT scenario, channel is closed without response
- For validation/internal errors, MessageHandler attempts ISO error response (RC 96 or 30)

### 5.2 REST Simulation Flow

POST /api/simulator/send -> SimulatorService -> MessageBuilder -> MessageHandler.process

Technical details:

- REST path throws exceptions to API layer (handled by ApiExceptionHandler)
- Scenario timeout maps to HTTP 504
- Validation errors map to HTTP 400

## 6. Validation, Rule, and Response Logic

### Validation order in MessageHandler

1. Parse bytes to ISOMsg (Iso8583Parser)
2. Load MTI config (ConfigManager)
3. Bitmap validation (BitmapValidator)
4. Field validation (FieldValidator)

### FieldValidator behavior

- Rejects unexpected fields not listed in requestFields
- Enforces mandatory presence
- Enforces non-empty values
- Enforces max length (value length <= configured length)
- Type checks for NUMERIC, PAN, DATETIME, ALPHA

### ResponseGenerator behavior

- Resolves response MTI from config.responseMti or MtiRouter
- Copies DE11 and DE37 from request when present
- Applies responseFields
- Applies defaultFields (if configured)
- Applies RuleEngine result to DE39 override
- Enforces response bitmap: removes fields not in bitmap.responseBits
- Guarantees DE39 exists (defaults to 96 if missing)

### Supported template values in response fields

- ${RRN}
- ${DATETIME} (MMddHHmmss)
- ${DATE} (yyyyMMdd)
- ${TIME} (HHmmss)
- ${STAN}
- ${REQUEST\_<n>} for pass-through request field values

### ScenarioEngine behavior

- DELAY: sleeps for configured delay ms (default 1000)
- TIMEOUT: sleeps for delay ms (default 60000), returns timeout result
- NONE or unknown: continue

## 7. Transaction State and Logging

### TransactionStore (in-memory)

- Saves transaction by STAN (max 5000, oldest evicted)
- Used for reversal marking logic in MessageHandler
- No dedicated REST endpoint for current TransactionStore state

### TransactionLogStore (in-memory)

- Stores MessageLog entries (max 1000)
- Exposed by GET /api/transactions

### MessageLog fields

- connectionId, mti, requestFields, responseFields, responseCode, timestamp, processingTime

## 8. REST API Reference (Current)

Base controllers:

- /api/config
- /api/bitmap
- /api/field
- /api/simulator
- /api/transactions

Config and behavior endpoints:

1. GET /api/config
2. GET /api/config/{mti}
3. POST /api/config/reload
4. POST /api/config/import
5. GET /api/config/export
6. GET /api/config/mti/{mti}
7. POST /api/config/mti
8. POST /api/config/{mti}/response-field
9. POST /api/config/{mti}/request-field
10. POST /api/config/{mti}/bitmap
11. DELETE /api/config/mti/{mti}
12. POST /api/config/{mti}/scenario
13. POST /api/config/{mti}/rule
14. DELETE /api/config/{mti}/rule/{field}
15. DELETE /api/config/{mti}/rule/id/{ruleId}
16. DELETE /api/config/{mti}/{field}

Additional endpoints:

1. POST /api/bitmap/{mti}
2. POST /api/field/{mti} (legacy convenience endpoint, adds response field)
3. POST /api/simulator/send
4. GET /api/transactions

Error mapping (ApiExceptionHandler):

- IsoValidationException -> 400
- ScenarioTimeoutException -> 504
- RuntimeException -> 500

## 9. Data Models Used in API

### MessageTypeConfig

- mti: String
- responseMti: String
- bitmap: BitmapConfig
- requestFields: List<FieldConfig>
- responseFields: List<FieldConfig>
- defaultFields: Map<Integer, String>
- rules: List<ResponseRule>
- scenario: ScenarioRule

### FieldConfig

- field: int
- type: String
- value: String
- mandatory: boolean
- length: int
- format: String

### BitmapConfig

- requestBits: List<Integer>
- responseBits: List<Integer>
- secondaryBitmap: boolean

### ResponseRule

- ruleId: String
- field: int
- operator: = | > | < | startsWith | contains
- value: String
- responseCode: String

### ScenarioRule

- type: NONE | DELAY | TIMEOUT
- delay: int (ms)
- responseCode: String (present in model, not actively used by ScenarioEngine)

### SimulatorRequest / SimulatorResponse

- mti: String
- fields: Map<Integer, String>

## 10. Known Constraints and Risks

1. In-memory config only
   - API edits are lost on restart/reload

2. Limited automated test coverage
   - Current test suite is context-load only

3. Timeouts are coarse-grained
   - Scenario DELAY/TIMEOUT uses Thread.sleep in worker thread

4. Duplicate dependency declarations in pom.xml
   - Cleanup needed for long-term maintainability

5. README.md currently does not contain complete project documentation
   - Treat this PROJECT_CONTEXT.md as the primary onboarding source

## 11. Files New Chats Should Read First

1. src/main/java/com/payu/pgsim/handler/MessageHandler.java
2. src/main/java/com/payu/pgsim/generator/ResponseGenerator.java
3. src/main/java/com/payu/pgsim/config/ConfigManager.java
4. src/main/java/com/payu/pgsim/validator/FieldValidator.java
5. src/main/java/com/payu/pgsim/validator/BitmapValidator.java
6. src/main/java/com/payu/pgsim/tcp/TcpServerHandler.java
7. src/main/resources/message-config.json
8. src/main/resources/application.properties
9. src/main/resources/static/app.js
10. src/main/resources/static/index.html

## 12. Suggested Onboarding Prompt for a New Chat Session

Use this prompt template:

```text
You are onboarding into the pgsim project at /Users/abhaykumar/Downloads/pgsim.

Read PROJECT_CONTEXT.md first and treat it as source of truth.

Current objective:
<replace with your task>

Constraints:
- Keep changes minimal and focused.
- Preserve existing API behavior unless explicitly changing it.
- Run relevant validation (build/tests) after edits.

Before coding, summarize:
1) files you will touch
2) risk areas
3) verification steps
```

## 13. Quick Validation Checklist After Any Change

1. Build:

```bash
./mvnw clean test
```

2. Run app:

```bash
./mvnw spring-boot:run
```

3. Smoke checks:

- Open UI at /index.html
- GET /api/config returns MTI list
- POST /api/simulator/send returns ISO response JSON
- Python TCP test client receives responses for valid input
