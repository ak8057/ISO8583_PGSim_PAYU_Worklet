package com.payu.pgsim.service;

import com.payu.pgsim.model.BitmapConfig;
import com.payu.pgsim.model.FieldConfig;
import com.payu.pgsim.model.MessageTypeConfig;
import com.payu.pgsim.model.MtiProfile;
import com.payu.pgsim.model.ScenarioRule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central store for MTI Profiles.
 *
 * Profiles are the *preferred* unit of configuration. Each profile describes:
 *  - The request MTI + response MTI pair
 *  - Request-side bitmap (mandatory + optional bits) + field definitions
 *  - Response-side bitmap + echo / static / dynamic field resolution
 *  - Rules and scenario simulation
 *
 * On startup the service builds default profiles from the existing
 * message-config.json so that all existing MTIs are covered out of the box.
 * Additional profiles can be created/updated/deleted at runtime via REST.
 *
 * Profiles complement (but do NOT replace) the existing {@link com.payu.pgsim.config.ConfigManager}
 * – every profile write-back also updates the ConfigManager so the rest of
 * the pipeline continues to work unchanged.
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final com.payu.pgsim.config.ConfigManager configManager;

    /** profileId → MtiProfile */
    private final Map<String, MtiProfile> profiles = new LinkedHashMap<>();

    // ── Bootstrap ─────────────────────────────────────────────────────────────

    @PostConstruct
    public void init() {
        loadFromMessageConfig();
        log.info("ProfileService initialised with {} profiles", profiles.size());
    }

    /**
     * Converts the existing message-config.json (loaded by ConfigManager) into
     * MtiProfile objects so the profile view is available immediately with no
     * extra configuration files.
     */
    private void loadFromMessageConfig() {
        for (MessageTypeConfig cfg : configManager.getAllConfigs()) {
            MtiProfile p = toProfile(cfg);
            profiles.put(p.getProfileId(), p);
        }
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public Collection<MtiProfile> getAllProfiles() {
        return profiles.values();
    }

    public MtiProfile getProfile(String profileId) {
        return profiles.get(profileId);
    }

    public MtiProfile getProfileByRequestMti(String requestMti) {
        return profiles.values().stream()
                .filter(p -> requestMti != null && requestMti.equals(p.getRequestMti()))
                .findFirst()
                .orElse(null);
    }

    public MtiProfile saveProfile(MtiProfile profile) {
        if (profile.getProfileId() == null || profile.getProfileId().isBlank()) {
            profile.setProfileId(buildProfileId(profile.getRequestMti(), profile.getResponseMti()));
        }
        profiles.put(profile.getProfileId(), profile);
        syncToConfigManager(profile);
        log.info("Profile saved: {}", profile.getProfileId());
        return profile;
    }

    public void deleteProfile(String profileId) {
        profiles.remove(profileId);
        log.info("Profile deleted: {}", profileId);
    }

    // ── Conversion helpers ────────────────────────────────────────────────────

    /**
     * Converts an existing {@link MessageTypeConfig} into an {@link MtiProfile}.
     * This is a lossless round-trip: every field visible in the classic config
     * is represented in the profile.
     */
    public static MtiProfile toProfile(MessageTypeConfig cfg) {
        MtiProfile p = new MtiProfile();

        p.setRequestMti(cfg.getMti());
        p.setResponseMti(cfg.getResponseMti());
        p.setProfileId(buildProfileId(cfg.getMti(), cfg.getResponseMti()));
        p.setName(resolveName(cfg.getMti()));
        p.setDescription(cfg.getDescription());

        // ── Request bitmap ────────────────────────────────────────────────────
        if (cfg.getBitmap() != null) {
            BitmapConfig bm = cfg.getBitmap();
            // Prefer mandatoryBits if present, fall back to requestBits
            if (bm.getMandatoryBits() != null && !bm.getMandatoryBits().isEmpty()) {
                p.setMandatoryRequestBits(new ArrayList<>(bm.getMandatoryBits()));
                if (bm.getOptionalBits() != null) {
                    p.setOptionalRequestBits(new ArrayList<>(bm.getOptionalBits()));
                }
            } else if (bm.getRequestBits() != null && !bm.getRequestBits().isEmpty()) {
                p.setMandatoryRequestBits(new ArrayList<>(bm.getRequestBits()));
                p.setOptionalRequestBits(new ArrayList<>());
            }
            if (bm.getResponseBits() != null) {
                p.setResponseBits(new ArrayList<>(bm.getResponseBits()));
            }
            p.setSecondaryBitmap(bm.isSecondaryBitmap());
        }

        // ── Request fields ────────────────────────────────────────────────────
        if (cfg.getRequestFields() != null) {
            p.setRequestFields(new ArrayList<>(cfg.getRequestFields()));
        }

        // ── Response fields – split into echo / static / dynamic ──────────────
        List<Integer> echoFields   = new ArrayList<>();
        List<FieldConfig> staticFs  = new ArrayList<>();
        List<FieldConfig> dynamicFs = new ArrayList<>();

        if (cfg.getResponseFields() != null) {
            for (FieldConfig rf : cfg.getResponseFields()) {
                String mode = rf.getMode() != null ? rf.getMode().toUpperCase() : "STATIC";
                switch (mode) {
                    case "FROM_REQUEST":
                        // echo: source DE number stored in value or sourceField
                        int srcField = rf.getSourceField() != null && rf.getSourceField() > 0
                                ? rf.getSourceField()
                                : rf.getField();
                        echoFields.add(srcField);
                        break;
                    case "DYNAMIC":
                        dynamicFs.add(rf);
                        break;
                    default:
                        staticFs.add(rf);
                        break;
                }
            }
        }

        // Always echo STAN (DE11) and RRN (DE37) if not already listed
        if (!echoFields.contains(11)) echoFields.add(11);
        if (!echoFields.contains(37)) echoFields.add(37);

        p.setEchoFields(echoFields);
        p.setStaticResponseFields(staticFs.isEmpty() ? null : staticFs);
        p.setDynamicResponseFields(dynamicFs.isEmpty() ? null : dynamicFs);

        // ── Rules / Scenario ──────────────────────────────────────────────────
        if (cfg.getRules() != null) {
            p.setRules(new ArrayList<>(cfg.getRules()));
        }
        p.setScenario(cfg.getScenario());

        // ── Client default fields ─────────────────────────────────────────────
        if (cfg.getFieldConfigs() != null && !cfg.getFieldConfigs().isEmpty()) {
            p.setClientDefaultFields(new ArrayList<>(cfg.getFieldConfigs()));
        } else if (cfg.getRequestFields() != null) {
            // Populate with request fields that have static values as client defaults
            List<FieldConfig> clientDefaults = new ArrayList<>();
            for (FieldConfig rf : cfg.getRequestFields()) {
                if (rf.getValue() != null && !rf.getValue().isBlank()) {
                    clientDefaults.add(rf);
                }
            }
            if (!clientDefaults.isEmpty()) {
                p.setClientDefaultFields(clientDefaults);
            }
        }

        return p;
    }

    /**
     * Converts a profile back into a {@link MessageTypeConfig} and writes it to
     * the ConfigManager so the rest of the pipeline continues to work unchanged.
     */
    void syncToConfigManager(MtiProfile profile) {
        if (profile.getRequestMti() == null) return;

        MessageTypeConfig cfg = new MessageTypeConfig();
        cfg.setMti(profile.getRequestMti());
        cfg.setResponseMti(profile.getResponseMti());
        cfg.setDescription(profile.getName());

        // Bitmap
        BitmapConfig bm = new BitmapConfig();
        bm.setMandatoryBits(profile.getMandatoryRequestBits() != null
                ? new ArrayList<>(profile.getMandatoryRequestBits()) : new ArrayList<>());
        bm.setOptionalBits(profile.getOptionalRequestBits() != null
                ? new ArrayList<>(profile.getOptionalRequestBits()) : new ArrayList<>());
        // requestBits = mandatory ∪ optional (for backwards-compat validators)
        List<Integer> reqBits = new ArrayList<>();
        if (profile.getMandatoryRequestBits() != null) reqBits.addAll(profile.getMandatoryRequestBits());
        if (profile.getOptionalRequestBits() != null) reqBits.addAll(profile.getOptionalRequestBits());
        bm.setRequestBits(reqBits);
        bm.setResponseBits(profile.getResponseBits() != null
                ? new ArrayList<>(profile.getResponseBits()) : new ArrayList<>());
        bm.setSecondaryBitmap(profile.isSecondaryBitmap());
        cfg.setBitmap(bm);

        // Request fields
        cfg.setRequestFields(profile.getRequestFields() != null
                ? new ArrayList<>(profile.getRequestFields()) : new ArrayList<>());

        // Response fields – merge echo / static / dynamic back into one list
        List<FieldConfig> responseFields = new ArrayList<>();
        if (profile.getEchoFields() != null) {
            for (Integer ef : profile.getEchoFields()) {
                FieldConfig fc = new FieldConfig();
                fc.setField(ef);
                fc.setMode("FROM_REQUEST");
                fc.setValue(String.valueOf(ef));
                responseFields.add(fc);
            }
        }
        if (profile.getStaticResponseFields() != null) {
            responseFields.addAll(profile.getStaticResponseFields());
        }
        if (profile.getDynamicResponseFields() != null) {
            responseFields.addAll(profile.getDynamicResponseFields());
        }
        cfg.setResponseFields(responseFields);

        // Rules / Scenario
        cfg.setRules(profile.getRules() != null ? new ArrayList<>(profile.getRules()) : new ArrayList<>());
        if (profile.getScenario() != null) {
            cfg.setScenario(profile.getScenario());
        } else {
            ScenarioRule none = new ScenarioRule();
            none.setType("NONE");
            cfg.setScenario(none);
        }

        // Client default fields
        if (profile.getClientDefaultFields() != null) {
            cfg.setFieldConfigs(new ArrayList<>(profile.getClientDefaultFields()));
        }

        configManager.updateConfig(cfg);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public static String buildProfileId(String requestMti, String responseMti) {
        return (requestMti != null ? requestMti : "XXXX")
                + "-"
                + (responseMti != null ? responseMti : "XXXX");
    }

    private static String resolveName(String mti) {
        if (mti == null) return "Unknown Profile";
        switch (mti) {
            case "0100": return "Authorization Profile";
            case "0110": return "Authorization Response";
            case "0200": return "Financial (Purchase) Profile";
            case "0210": return "Financial Response";
            case "0220": return "Financial Advice Profile";
            case "0221": return "Financial Advice Repeat Profile";
            case "0240": return "Financial Advice Response";
            case "0400": return "Reversal Request Profile";
            case "0410": return "Reversal Response";
            case "0420": return "Reversal Advice Profile";
            case "0421": return "Reversal Advice Repeat Profile";
            case "0430": return "Reversal Advice Response";
            case "0600": return "Administrative Request Profile";
            case "0610": return "Administrative Response";
            case "0620": return "Administrative Advice Profile";
            case "0630": return "Administrative Advice Response";
            case "0800": return "Network Management Request Profile";
            case "0810": return "Network Management Response";
            case "0820": return "Network Management Advice Profile";
            case "0830": return "Network Management Advice Response";
            default:     return "MTI " + mti + " Profile";
        }
    }
}
