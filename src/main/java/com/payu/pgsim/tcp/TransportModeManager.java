package com.payu.pgsim.tcp;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@ConditionalOnProperty(name = "simulator.tcp.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class TransportModeManager {

    private static final Logger log = LoggerFactory.getLogger(TransportModeManager.class);

    private final TcpServer tcpServer;
    private final TcpClient tcpClient;

    @Value("${simulator.mode:SERVER}")
    private String configuredMode;

    private volatile String currentMode = "SERVER";

    @PostConstruct
    public void init() {
        switchMode(configuredMode);
    }

    public synchronized String switchMode(String mode) {
        String normalized = normalize(mode);
        if ("SERVER".equals(normalized)) {
            tcpClient.stop();
            tcpServer.start();
        } else {
            tcpServer.stop();
            tcpClient.start();
        }
        currentMode = normalized;
        log.info("Runtime transport mode switched to {}", currentMode);
        return currentMode;
    }

    public String getCurrentMode() {
        return currentMode;
    }

    private String normalize(String mode) {
        if (mode == null) {
            return "SERVER";
        }
        String m = mode.trim().toUpperCase(Locale.ROOT);
        return "CLIENT".equals(m) ? "CLIENT" : "SERVER";
    }
}

