package com.payu.pgsim.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payu.pgsim.model.BitmapConfig;
import com.payu.pgsim.model.FieldConfig;
import com.payu.pgsim.model.MessageTypeConfig;
import com.payu.pgsim.model.ResponseRule;
import com.payu.pgsim.model.ScenarioRule;
import com.payu.pgsim.validator.ConfigValidator;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import com.payu.pgsim.model.ConfigurationExport;
import com.payu.pgsim.model.ImportResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
public class ConfigManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);

    private final Map<String, MessageTypeConfig> configs = new ConcurrentHashMap<>();
    private final ConfigValidator configValidator;
    private final RuntimeConfigurationPersistence runtimeConfigurationPersistence;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AtomicLong configurationVersion = new AtomicLong(0);

    /**
     * Load configuration on application startup
     */
    @PostConstruct
    public void init() {
        loadConfigs();
        overlayFromPersistenceIfAny();
    }

    /**
     * Loads message configuration from JSON file
     */
    public synchronized void loadConfigs() {

        try {

            InputStream is = getClass()
                    .getClassLoader()
                    .getResourceAsStream("message-config.json");

            if (is == null) {
                throw new RuntimeException(
                        "message-config.json not found in resources folder");
            }

            List<MessageTypeConfig> list = objectMapper.readValue(
                    is,
                    new TypeReference<List<MessageTypeConfig>>() {
                    });

            configs.clear();

            for (MessageTypeConfig config : list) {

                normalizeForRuntime(config);

                configValidator.validate(config);
                configs.put(config.getMti(), config);

                log.info("Loaded MTI config: {}", config.getMti());
            }

            log.info("Total message configs loaded: {}", configs.size());
            bumpConfigurationVersion();

        } catch (Exception e) {

            log.error("Failed to load message configuration", e);

            throw new RuntimeException("Configuration loading failed", e);
        }
    }

    public long getConfigurationVersion() {
        return configurationVersion.get();
    }

    private void bumpConfigurationVersion() {
        configurationVersion.incrementAndGet();
    }

    private void bumpAndPersist() {
        bumpConfigurationVersion();
        runtimeConfigurationPersistence.persistIfEnabled(new ArrayList<>(configs.values()));
    }

    private void overlayFromPersistenceIfAny() {
        Optional<List<MessageTypeConfig>> disk = runtimeConfigurationPersistence.loadIfPresent();
        if (disk.isEmpty()) {
            return;
        }
        configs.clear();
        for (MessageTypeConfig config : disk.get()) {
            normalizeForRuntime(config);
            configValidator.validate(config);
            configs.put(config.getMti(), config);
            log.info("Restored MTI from persistence: {}", config.getMti());
        }
        bumpConfigurationVersion();
        log.info("Runtime configuration restored from {} ({} MTIs)",
                runtimeConfigurationPersistence.getPath(), configs.size());
    }

    public synchronized ImportResult importConfigurationExport(ConfigurationExport export) {
        if (export == null || export.getMessageTypes() == null) {
            ImportResult bad = new ImportResult();
            bad.setSuccess(false);
            bad.getErrors().add("messageTypes is required");
            return bad;
        }
        return importMessageTypes(export.getMessageTypes());
    }

    public synchronized ImportResult importMessageTypes(List<MessageTypeConfig> messageTypes) {
        ImportResult result = new ImportResult();
        int n = 0;
        for (MessageTypeConfig config : messageTypes) {
            if (config == null) {
                result.getErrors().add("null entry skipped");
                continue;
            }
            try {
                normalizeForRuntime(config);
                configValidator.validate(config);
                configs.put(config.getMti(), config);
                n++;
            } catch (Exception e) {
                String mti = config.getMti() != null ? config.getMti() : "?";
                result.getErrors().add(mti + ": " + e.getMessage());
                log.warn("Import failed for MTI {}: {}", mti, e.getMessage());
            }
        }
        result.setImportedCount(n);
        result.setSuccess(result.getErrors().isEmpty());
        bumpAndPersist();
        return result;
    }

    /**
     * Reload configuration without restarting server
     */
    public synchronized void reload() {

        log.info("Reloading configuration...");

        loadConfigs();
        runtimeConfigurationPersistence.persistIfEnabled(new ArrayList<>(configs.values()));

        log.info("Configuration reloaded successfully");
    }

    /**
     * Returns configuration for a specific MTI
     */
    public MessageTypeConfig getConfig(String mti) {

        MessageTypeConfig config = configs.get(mti);

        if (config == null) {
            log.info("No config found for MTI: {}", mti);
        }

        return config;
    }

    public Collection<MessageTypeConfig> getAllConfigs() {
        return configs.values();
    }

    public void saveConfig(MessageTypeConfig config) {
        normalizeForRuntime(config);
        configValidator.validate(config);
        configs.put(config.getMti(), config);
        bumpAndPersist();
    }

    public void deleteConfig(String mti) {

        configs.remove(mti);

        log.info("Deleted config for MTI: {}", mti);
        bumpAndPersist();
    }

    public void updateBitmap(String mti, BitmapConfig bitmap) {

        MessageTypeConfig config = configs.get(mti);

        if (config == null) {
            throw new RuntimeException("MTI config not found: " + mti);
        }

        config.setBitmap(bitmap);

        configValidator.validate(config);
        log.info("Bitmap updated for MTI: {}", mti);
        bumpAndPersist();
    }

    public void addResponseField(String mti, FieldConfig field) {

        MessageTypeConfig config = configs.get(mti);

        if (config == null) {
            throw new RuntimeException("MTI config not found: " + mti);
        }

        if (config.getResponseFields() == null) {
            config.setResponseFields(new ArrayList<>());
        }
        config.getResponseFields()
                .removeIf(f -> f.getField() == field.getField());

        config.getResponseFields().add(field);

        configValidator.validate(config);
        log.info("Field added to MTI: {}", mti);
        bumpAndPersist();
    }

    public void addRequestField(String mti, FieldConfig field) {

    MessageTypeConfig config = configs.get(mti);

    if (config == null) {
        throw new RuntimeException("MTI config not found: " + mti);
    }

    if (config.getRequestFields() == null) {
        config.setRequestFields(new ArrayList<>());
    }

    config.getRequestFields()
          .removeIf(f -> f.getField() == field.getField());

    config.getRequestFields().add(field);

    configValidator.validate(config);
    log.info("Request field added to MTI: {}", mti);
    bumpAndPersist();
}

    public void updateConfig(MessageTypeConfig config) {

        normalizeForRuntime(config);
        configValidator.validate(config);
        configs.put(config.getMti(), config);

        log.info("Runtime config updated for MTI: {}", config.getMti());
        bumpAndPersist();

    }

    public void addRule(String mti, ResponseRule rule) {

        MessageTypeConfig config = configs.get(mti);
        if (config == null) {
            throw new RuntimeException("MTI config not found: " + mti);
        }

        if (config.getRules() == null) {
            config.setRules(new ArrayList<>());
        }

        registerRule(config.getRules(), rule);
        configValidator.validate(config);
        bumpAndPersist();

    }

    private void normalizeAndRegisterRules(MessageTypeConfig config) {
        if (config == null) {
            return;
        }

        if (config.getRules() == null) {
            config.setRules(new ArrayList<>());
            return;
        }

        List<ResponseRule> normalizedRules = new ArrayList<>();
        for (ResponseRule rule : config.getRules()) {
            registerRule(normalizedRules, rule);
        }
        config.setRules(normalizedRules);
    }

    private void normalizeForRuntime(MessageTypeConfig config) {
        if (config == null) {
            return;
        }
        normalizeOutgoingRequestProfile(config);
        normalizeAndRegisterRules(config);
        normalizeRequestBitmap(config);
    }

    /**
     * Supports shorthand outgoing profile payloads:
     * {
     *   "mti": "0200",
     *   "requestBits": [2,3,4,7,11],
     *   "fieldConfigs": [...]
     * }
     * These aliases are normalized into the canonical runtime shape used by validators/builders.
     */
    private void normalizeOutgoingRequestProfile(MessageTypeConfig config) {
        if ((config.getRequestFields() == null || config.getRequestFields().isEmpty())
                && config.getFieldConfigs() != null && !config.getFieldConfigs().isEmpty()) {
            config.setRequestFields(new ArrayList<>(config.getFieldConfigs()));
        }
        if (config.getBitmap() == null) {
            config.setBitmap(new BitmapConfig());
        }
        if ((config.getBitmap().getRequestBits() == null || config.getBitmap().getRequestBits().isEmpty())
                && config.getRequestBits() != null && !config.getRequestBits().isEmpty()) {
            config.getBitmap().setRequestBits(new ArrayList<>(config.getRequestBits()));
        }
    }

    private void normalizeRequestBitmap(MessageTypeConfig config) {
        if (config.getBitmap() == null) {
            return;
        }
        if (config.getBitmap().getRequestBits() == null || config.getBitmap().getRequestBits().isEmpty()) {
            List<Integer> bits = new ArrayList<>();
            if (config.getRequestFields() != null) {
                for (FieldConfig f : config.getRequestFields()) {
                    if (f != null && f.getField() > 0) {
                        bits.add(f.getField());
                    }
                }
            }
            config.getBitmap().setRequestBits(bits);
        }
    }

    private void registerRule(List<ResponseRule> rulesStore, ResponseRule rule) {
        if (rule == null || rulesStore == null) {
            return;
        }

        if (rule.getRuleId() == null || rule.getRuleId().isBlank()) {
            rule.setRuleId(UUID.randomUUID().toString());
        }

        rulesStore.add(rule);
    }

    public void updateScenario(String mti, ScenarioRule scenario) {

        MessageTypeConfig config = configs.get(mti);
        if (config == null) {
            throw new RuntimeException("MTI config not found: " + mti);
        }

        config.setScenario(scenario);
        configValidator.validate(config);
        bumpAndPersist();

    }

    public void deleteRule(String mti, int field) {

        MessageTypeConfig config = configs.get(mti);

        if (config == null || config.getRules() == null) {
            return;
        }

        config.getRules().removeIf(
                rule -> rule.getField() == field);
        bumpAndPersist();

    }

    public void deleteRuleById(String mti, String ruleId) {
        MessageTypeConfig config = configs.get(mti);
        if (config == null || config.getRules() == null) {
            return;
        }
        config.getRules().removeIf(rule -> ruleId != null && ruleId.equals(rule.getRuleId()));
        bumpAndPersist();
    }

    public void deleteField(String mti, int field) {

        MessageTypeConfig config = configs.get(mti);

        if (config == null) 
            return;

        // Remove from request fields
        if (config.getRequestFields() != null) {
            config.getRequestFields().removeIf(f -> f.getField() == field);
        }

        // Remove from response fields
        if (config.getResponseFields() != null) {
            config.getResponseFields().removeIf(f -> f.getField() == field);
        }

        log.info("Field {} deleted from MTI: {}", field, mti);
        bumpAndPersist();
    }

}