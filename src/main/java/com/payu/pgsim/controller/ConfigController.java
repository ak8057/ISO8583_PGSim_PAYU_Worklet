package com.payu.pgsim.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payu.pgsim.config.ConfigManager;
import com.payu.pgsim.model.BitmapConfig;
import com.payu.pgsim.model.ConfigurationExport;
import com.payu.pgsim.model.FieldConfig;
import com.payu.pgsim.model.FieldDefinition;
import com.payu.pgsim.model.ImportResult;
import com.payu.pgsim.model.MessageTypeConfig;
import com.payu.pgsim.model.ScenarioRule;
import com.payu.pgsim.model.ValidationResult;
import com.payu.pgsim.service.IsoFieldCatalog;
import com.payu.pgsim.validator.ConfigValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigManager configManager;
    private final ConfigValidator configValidator;
    private final IsoFieldCatalog isoFieldCatalog;
    private final ObjectMapper objectMapper;

    @GetMapping
    public Collection<MessageTypeConfig> getAllConfigs() {
        return configManager.getAllConfigs();
    }

    /**
     * BRD §8.2.1 — list configured message types (same payload as GET /api/config).
     */
    @GetMapping("/message-types")
    public Collection<MessageTypeConfig> listMessageTypesBrd() {
        return configManager.getAllConfigs();
    }

    /**
     * BRD §8.2.1 — get one MTI (avoids collision with legacy {@code /{mti}}).
     */
    @GetMapping("/message-types/{mti}")
    public MessageTypeConfig getMessageTypeBrd(@PathVariable("mti") String mti) {
        return configManager.getConfig(mti);
    }

    /**
     * BRD §8.2.1 — create/update full message type configuration.
     */
    @PostMapping("/message-types/{mti}")
    public MessageTypeConfig upsertMessageTypeBrd(
            @PathVariable("mti") String mti,
            @RequestBody MessageTypeConfig body) {
        body.setMti(mti);
        configManager.updateConfig(body);
        return body;
    }

    /**
     * BRD §8.2.1 — delete message type configuration.
     */
    @DeleteMapping("/message-types/{mti}")
    public ResponseEntity<Void> deleteMessageTypeBrd(@PathVariable("mti") String mti) {
        configManager.deleteConfig(mti);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/fields")
    public List<FieldDefinition> listFieldDefinitions() {
        return isoFieldCatalog.all();
    }

    @PostMapping("/validate")
    public ValidationResult validateConfiguration(@RequestBody MessageTypeConfig config) {
        return configValidator.validateDetailed(config);
    }

    /**
     * Accepts: (1) single {@link MessageTypeConfig}, (2) array of configs, or (3) {@link ConfigurationExport}.
     */
    @PostMapping("/import")
    public ResponseEntity<ImportResult> importFlexible(@RequestBody JsonNode node) {
        if (node == null || node.isNull()) {
            return ResponseEntity.badRequest().body(failedImport("empty body"));
        }
        if (node.isArray()) {
            List<MessageTypeConfig> list = objectMapper.convertValue(node, new TypeReference<>() {
            });
            return ResponseEntity.ok(configManager.importMessageTypes(list));
        }
        if (node.has("messageTypes")) {
            ConfigurationExport ex = objectMapper.convertValue(node, ConfigurationExport.class);
            return ResponseEntity.ok(configManager.importConfigurationExport(ex));
        }
        MessageTypeConfig single = objectMapper.convertValue(node, MessageTypeConfig.class);
        configManager.saveConfig(single);
        ImportResult ok = new ImportResult();
        ok.setSuccess(true);
        ok.setImportedCount(1);
        return ResponseEntity.ok(ok);
    }

    private static ImportResult failedImport(String msg) {
        ImportResult r = new ImportResult();
        r.setSuccess(false);
        r.getErrors().add(msg);
        return r;
    }

    @GetMapping("/{mti}")
    public MessageTypeConfig getConfig(@PathVariable("mti") String mti) {
        return configManager.getConfig(mti);
    }

    private final org.springframework.beans.factory.ObjectProvider<com.payu.pgsim.tcp.TransportModeManager> transportModeManager;
    private final org.springframework.beans.factory.ObjectProvider<com.payu.pgsim.service.ProfileClientService> profileClientService;

    @GetMapping("/profile/{mti}")
    public MessageTypeConfig getProfileForClient(@PathVariable("mti") String mti) {
        String mode = "SERVER";
        if (transportModeManager.getIfAvailable() != null) {
            mode = transportModeManager.getIfAvailable().getCurrentMode();
        }

        if ("CLIENT".equalsIgnoreCase(mode) && profileClientService.getIfAvailable() != null) {
            MessageTypeConfig serverConfig = profileClientService.getIfAvailable().fetchProfileFromServer(mti);
            if (serverConfig != null) {
                return serverConfig;
            }
        }
        
        return configManager.getConfig(mti);
    }

    @PostMapping("/reload")
    public String reload() {
        configManager.reload();
        return "Configuration reloaded";
    }

    @GetMapping("/export")
    public Collection<MessageTypeConfig> exportConfig() {
        return configManager.getAllConfigs();
    }

    @GetMapping("/mti/{mti}")
    public MessageTypeConfig getMessageTypeConfig(@PathVariable("mti") String mti) {
        return configManager.getConfig(mti);
    }

    @PostMapping("/mti")
    public String saveConfig(@RequestBody MessageTypeConfig config) {
        configManager.updateConfig(config);
        return "Config updated";
    }

    @PostMapping("/{mti}/response-field")
    public String addResponseField(@PathVariable("mti") String mti, @RequestBody FieldConfig field) {
        configManager.addResponseField(mti, field);
        return "Field added";
    }

    @PostMapping("/{mti}/request-field")
    public String addRequestField(@PathVariable("mti") String mti, @RequestBody FieldConfig field) {
        configManager.addRequestField(mti, field);
        return "Request field added";
    }

    @PostMapping("/{mti}/bitmap")
    public String updateBitmap(@PathVariable("mti") String mti, @RequestBody BitmapConfig bitmap) {
        configManager.updateBitmap(mti, bitmap);
        return "Bitmap updated";
    }

    @DeleteMapping("/mti/{mti}")
    public String deleteMessageTypeConfig(@PathVariable("mti") String mti) {
        configManager.deleteConfig(mti);
        return "Configuration deleted";
    }

    @PostMapping("/{mti}/scenario")
    public String updateScenario(@PathVariable("mti") String mti, @RequestBody ScenarioRule scenario) {
        configManager.updateScenario(mti, scenario);
        return "Scenario updated";
    }

    @DeleteMapping("/{mti}/rule/{field}")
    public void deleteRule(@PathVariable("mti") String mti, @PathVariable("field") int field) {
        configManager.deleteRule(mti, field);
    }

    @DeleteMapping("/{mti}/rule/id/{ruleId}")
    public void deleteRuleById(@PathVariable("mti") String mti, @PathVariable("ruleId") String ruleId) {
        configManager.deleteRuleById(mti, ruleId);
    }

    @DeleteMapping("/{mti}/{field}")
    public void deleteField(@PathVariable("mti") String mti, @PathVariable("field") int field) {
        configManager.deleteField(mti, field);
    }
}
