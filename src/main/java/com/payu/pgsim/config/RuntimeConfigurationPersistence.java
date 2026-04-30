package com.payu.pgsim.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payu.pgsim.model.MessageTypeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Optional persistence of the in-memory MTI configuration to JSON (BRD operational expectation).
 */
@Component
public class RuntimeConfigurationPersistence {

    private static final Logger log = LoggerFactory.getLogger(RuntimeConfigurationPersistence.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final boolean enabled;
    private final Path filePath;

    public RuntimeConfigurationPersistence(
            @Value("${pgsim.config.persistence.enabled:false}") boolean enabled,
            @Value("${pgsim.config.persistence.path:./data/runtime-message-config.json}") String path) {
        this.enabled = enabled;
        this.filePath = Path.of(path).toAbsolutePath().normalize();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getPath() {
        return filePath.toString();
    }

    public boolean fileExists() {
        return Files.isRegularFile(filePath);
    }

    public Optional<List<MessageTypeConfig>> loadIfPresent() {
        if (!enabled || !fileExists()) {
            return Optional.empty();
        }
        try {
            List<MessageTypeConfig> list = objectMapper.readValue(
                    Files.newInputStream(filePath),
                    new TypeReference<List<MessageTypeConfig>>() {
                    });
            log.info("Loaded {} MTI configs from {}", list != null ? list.size() : 0, filePath);
            return Optional.ofNullable(list).filter(l -> !l.isEmpty());
        } catch (IOException e) {
            log.warn("Could not read persisted config from {}: {}", filePath, e.getMessage());
            return Optional.empty();
        }
    }

    public void persistIfEnabled(List<MessageTypeConfig> configs) {
        if (!enabled) {
            return;
        }
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), configs);
            log.debug("Persisted {} MTI configs to {}", configs.size(), filePath);
        } catch (IOException e) {
            log.warn("Could not persist configuration to {}: {}", filePath, e.getMessage());
        }
    }
}
