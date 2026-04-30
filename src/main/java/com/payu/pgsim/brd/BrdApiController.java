package com.payu.pgsim.brd;

import com.payu.pgsim.brd.dto.BrdConnectionInfo;
import com.payu.pgsim.brd.dto.BrdMessageLog;
import com.payu.pgsim.brd.dto.BrdMessageTypeConfig;
import com.payu.pgsim.brd.dto.BrdSimulatorStatus;
import com.payu.pgsim.config.ConfigManager;
import com.payu.pgsim.model.MessageLog;
import com.payu.pgsim.model.MessageTypeConfig;
import com.payu.pgsim.model.ValidationResult;
import com.payu.pgsim.service.RuntimeStats;
import com.payu.pgsim.store.ConnectionStore;
import com.payu.pgsim.store.MessageLogStore;
import com.payu.pgsim.tcp.TlsSupport;
import com.payu.pgsim.tcp.TransportModeManager;
import com.payu.pgsim.validator.ConfigValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * BRD §8.2 — JSON shapes per §10 (nested requestConfig/responseConfig).
 */
@RestController
@RequestMapping("/api/brd/v1")
@RequiredArgsConstructor
public class BrdApiController {

    private final ConfigManager configManager;
    private final BrdConfigMapper brdConfigMapper;
    private final ConfigValidator configValidator;
    private final RuntimeStats runtimeStats;
    private final ConnectionStore connectionStore;
    private final MessageLogStore messageLogStore;
    private final BrdLogMapper brdLogMapper;
    private final TlsSupport tlsSupport;
    private final ObjectProvider<TransportModeManager> transportModeManager;

    @Value("${simulator.tcp.port:8080}")
    private int simulatorTcpPort;

    @Value("${simulator.client.port:8082}")
    private int simulatorClientPort;

    @Value("${pgsim.iso.primary-packager:iso87ascii.xml}")
    private String isoPrimaryPackager;

    @Value("${pgsim.iso.secondary-packager:}")
    private String isoSecondaryPackager;

    @Value("${server.port:8081}")
    private int httpPort;

    @GetMapping("/config/message-types")
    public List<BrdMessageTypeConfig> listMessageTypes() {
        return configManager.getAllConfigs().stream()
                .map(brdConfigMapper::toBrd)
                .collect(Collectors.toList());
    }

    @GetMapping("/config/message-types/{mti}")
    public ResponseEntity<BrdMessageTypeConfig> getOne(@PathVariable String mti) {
        MessageTypeConfig c = configManager.getConfig(mti);
        if (c == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(brdConfigMapper.toBrd(c));
    }

    @PostMapping("/config/message-types/{mti}")
    public BrdMessageTypeConfig upsert(@PathVariable String mti, @RequestBody BrdMessageTypeConfig body) {
        body.setMti(mti);
        MessageTypeConfig internal = brdConfigMapper.toInternal(body);
        configManager.updateConfig(internal);
        return brdConfigMapper.toBrd(internal);
    }

    @DeleteMapping("/config/message-types/{mti}")
    public ResponseEntity<Void> delete(@PathVariable String mti) {
        configManager.deleteConfig(mti);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/config/validate")
    public ValidationResult validate(@RequestBody BrdMessageTypeConfig body) {
        MessageTypeConfig internal = brdConfigMapper.toInternal(body);
        return configValidator.validateDetailed(internal);
    }

    @GetMapping("/runtime/status")
    public BrdSimulatorStatus status() {
        int active = connectionStore.getAll().size();
        long cfg = configManager.getAllConfigs().size();
        long ver = configManager.getConfigurationVersion();
        var s = runtimeStats.getStatus(active, simulatorTcpPort, simulatorClientPort, cfg, ver);
        BrdSimulatorStatus b = new BrdSimulatorStatus();
        b.setStatus(s.getStatus());
        b.setUptime(s.getUptime());
        b.setTcpPort(s.getTcpPort());
        b.setActiveConnections(s.getActiveConnections());
        b.setTotalMessagesReceived(s.getTotalMessagesReceived());
        b.setTotalMessagesSent(s.getTotalMessagesSent());
        b.setErrorCount(s.getErrorCount());
        b.setLastError(s.getLastError());
        b.setConfigurationCount(s.getConfigurationCount());
        b.setConfigurationVersion(s.getConfigurationVersion());
        b.setIsoPrimaryPackager(isoPrimaryPackager);
        b.setIsoSecondaryPackager(isoSecondaryPackager != null && !isoSecondaryPackager.isBlank() ? isoSecondaryPackager : null);
        b.setTcpTlsActive(tlsSupport.isTlsActive());
        b.setTcpTlsRequested(tlsSupport.isTlsRequested());
        b.setHttpPort(httpPort);
        b.setIsoEncodingNote("FR-2: ASCII primary + optional binary secondary unpack path.");
        TransportModeManager mm = transportModeManager.getIfAvailable();
        b.setSimulatorMode(mm != null ? mm.getCurrentMode() : "TCP_DISABLED");
        return b;
    }

    @GetMapping("/runtime/connections")
    public List<BrdConnectionInfo> connections() {
        return connectionStore.getAll().stream()
                .map(brdLogMapper::toBrd)
                .collect(Collectors.toList());
    }

    @GetMapping("/logs/messages")
    public List<BrdMessageLog> logs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String mti,
            @RequestParam(required = false, defaultValue = "0") int limit) {
        List<MessageLog> raw = messageLogStore.query(from, to, mti, limit);
        return raw.stream().map(brdLogMapper::toBrd).collect(Collectors.toList());
    }

    @GetMapping("/logs/messages/{id}")
    public ResponseEntity<BrdMessageLog> logById(@PathVariable String id) {
        return messageLogStore.getById(id)
                .map(brdLogMapper::toBrd)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
