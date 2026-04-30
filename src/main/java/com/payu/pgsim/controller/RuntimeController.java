package com.payu.pgsim.controller;

import com.payu.pgsim.config.ConfigManager;
import com.payu.pgsim.model.ConnectionInfo;
import com.payu.pgsim.model.MessageStatistics;
import com.payu.pgsim.model.SimulatorStatus;
import com.payu.pgsim.service.RuntimeStats;
import com.payu.pgsim.store.ConnectionStore;
import com.payu.pgsim.tcp.TlsSupport;
import com.payu.pgsim.tcp.TransportModeManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/runtime")
@RequiredArgsConstructor
public class RuntimeController {

    private final RuntimeStats runtimeStats;
    private final ConnectionStore connectionStore;
    private final ConfigManager configManager;
    private final TlsSupport tlsSupport;
    private final ObjectProvider<TransportModeManager> transportModeManager;

    @Value("${simulator.tcp.port:8080}")
    private int simulatorTcpServerPort;

    @Value("${simulator.client.port:8082}")
    private int simulatorTcpClientPort;

    @Value("${pgsim.iso.primary-packager:iso87ascii.xml}")
    private String isoPrimaryPackager;

    @Value("${pgsim.iso.secondary-packager:}")
    private String isoSecondaryPackager;

    @Value("${server.port:8081}")
    private int httpPort;

    @Value("${simulator.instance.role:}")
    private String configuredInstanceRole;

    @Value("${simulator.mode.switch-enabled:true}")
    private boolean modeSwitchEnabled;

    @GetMapping("/status")
    public SimulatorStatus getStatus() {
        int active = connectionStore.getAll().size();
        long cfgCount = configManager.getAllConfigs().size();
        long version = configManager.getConfigurationVersion();
        SimulatorStatus s = runtimeStats.getStatus(
                active,
                simulatorTcpServerPort,
                simulatorTcpClientPort,
                cfgCount,
                version);
        s.setIsoPrimaryPackager(isoPrimaryPackager);
        s.setIsoSecondaryPackager(isoSecondaryPackager != null && !isoSecondaryPackager.isBlank() ? isoSecondaryPackager : null);
        s.setTcpTlsActive(tlsSupport.isTlsActive());
        s.setTcpTlsRequested(tlsSupport.isTlsRequested());
        s.setHttpPort(httpPort);
        s.setIsoEncodingNote("FR-2: primary ASCII packager; optional secondary binary fallback when unpack fails.");
        TransportModeManager mm = transportModeManager.getIfAvailable();
        String currentMode = mm != null ? mm.getCurrentMode() : "TCP_DISABLED";
        s.setSimulatorMode(currentMode);
        s.setModeSwitchEnabled(modeSwitchEnabled);
        s.setInstanceRole(resolveInstanceRole(currentMode));
        return s;
    }

    @GetMapping("/statistics")
    public MessageStatistics getStatistics() {
        int active = connectionStore.getAll().size();
        long cfgCount = configManager.getAllConfigs().size();
        long version = configManager.getConfigurationVersion();
        return runtimeStats.getStatistics(active, cfgCount, version);
    }

    /**
     * BRD §8.2.2 — reload configurations (delegates to {@link ConfigManager#reload()}).
     */
    @PostMapping("/reload")
    public ResponseEntity<Void> reloadConfigurations() {
        configManager.reload();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/connections")
    public Collection<ConnectionInfo> getConnections() {
        return connectionStore.getAll();
    }

    @PostMapping("/mode")
    public ResponseEntity<Map<String, String>> setMode(@RequestBody Map<String, String> payload) {
        String requested = payload != null ? payload.get("mode") : null;
        TransportModeManager mm = transportModeManager.getIfAvailable();
        if (mm == null) {
            return ResponseEntity.ok(Map.of(
                    "mode", "TCP_DISABLED",
                    "message", "simulator.tcp.enabled=false, transport mode toggle unavailable"));
        }
        if (!modeSwitchEnabled) {
            String currentMode = mm.getCurrentMode();
            return ResponseEntity.badRequest().body(Map.of(
                    "mode", currentMode,
                    "message", "Mode switching is disabled for this deployment"));
        }
        String active = mm.switchMode(requested);
        return ResponseEntity.ok(Map.of("mode", active));
    }

    private String resolveInstanceRole(String currentMode) {
        if (configuredInstanceRole != null && !configuredInstanceRole.isBlank()) {
            String normalized = configuredInstanceRole.trim().toUpperCase(Locale.ROOT);
            if ("CLIENT".equals(normalized) || "SERVER".equals(normalized)) {
                return normalized;
            }
        }
        return currentMode;
    }
}
