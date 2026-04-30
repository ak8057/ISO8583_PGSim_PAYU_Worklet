# ISO8583 Simulator Demo Readiness Checklist

Use this checklist before showing the simulator to clients.

## 1) Build and startup

- Build artifact:
  - `mvn clean package`
- Start app for demo:
  - `mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8082 --simulator.tcp.port=9090 --simulator.mode=SERVER --simulator.tcp.enabled=true"`

## 2) Health and observability

- Health:
  - `GET /actuator/health` returns `UP`
- Metrics:
  - `GET /actuator/prometheus` returns Prometheus metrics payload
- Runtime:
  - `GET /api/runtime/status` confirms:
    - `simulatorMode`
    - `isoPrimaryPackager`
    - `isoSecondaryPackager`
    - `tcpTlsActive`

## 3) Functional smoke tests

- MTI config discovery:
  - `GET /api/config/message-types` returns configured MTIs
- Server mode simulation:
  - `POST /api/simulator/send` with MTI `0200`, check response MTI `0210`
- Logging:
  - `GET /api/logs/messages?limit=10`
  - Verify each row includes `direction` and `mode`

## 4) Client mode validation (if needed in demo)

- Switch mode:
  - `POST /api/runtime/mode` body: `{ "mode": "CLIENT" }`
- Ensure `simulator.client.host` and `simulator.client.port` point to reachable peer.
- Send from simulator in CLIENT mode and confirm:
  - outbound request log: `mode=CLIENT`, `direction=OUTGOING`
  - inbound response log: `mode=CLIENT`, `direction=INCOMING`

## 5) Production deployment controls

- Set API key in environment for restricted usage in shared environments.
- Keep runtime persistence enabled only when required:
  - `pgsim.config.persistence.enabled=true|false`
- Keep test and prod ports separate to avoid bind conflicts.
- If TLS is required:
  - configure `pgsim.tcp.tls.enabled=true`
  - configure keystore path, password, and type
  - verify handshake against a real external client

## 6) Known operational recommendations

- Use a process manager (systemd/Kubernetes) for restart policy.
- Add alerting for:
  - `errorCount` growth
  - connection churn
  - latency spikes
- Export logs and metrics into your central observability platform.

