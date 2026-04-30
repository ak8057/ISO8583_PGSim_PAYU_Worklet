package com.payu.pgsim.service;

import com.payu.pgsim.config.ConfigManager;
import com.payu.pgsim.generator.field.FieldValueResolver;
import com.payu.pgsim.model.FieldConfig;
import com.payu.pgsim.model.MessageTypeConfig;
import com.payu.pgsim.model.MtiProfile;
import com.payu.pgsim.model.SimulatorRequest;
import com.payu.pgsim.parser.Iso8583Parser;
import com.payu.pgsim.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.jpos.iso.ISOUtil;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OutgoingMessageBuilder {

    private final ConfigManager configManager;
    private final Iso8583Parser parser;
    private final FieldValueResolver fieldValueResolver;
    private final ProfileService profileService;

    public ISOMsg build(SimulatorRequest request) throws Exception {
        return build(request, null);
    }

    public ISOMsg build(SimulatorRequest request, MessageTypeConfig providedConfig) throws Exception {
        if (request == null || request.getMti() == null || request.getMti().isBlank()) {
            throw new IllegalArgumentException("Request MTI is required");
        }

        // If config is explicitly provided, we bypass the MtiProfile completely
        // and just use the legacy config path directly for this request.
        MessageTypeConfig config = providedConfig;

        if (config == null) {
            // ── Profile-driven path (preferred if local) ───────────────────────────────────
            MtiProfile profile = profileService.getProfileByRequestMti(request.getMti());
            if (profile != null) {
                return buildFromProfile(request, profile);
            }
            // ── Legacy config-driven path (fallback) ──────────────────────────────
            config = configManager.getConfig(request.getMti());
        }

        if (config == null) {
            return buildFromRaw(request);
        }

        ISOMsg msg = new ISOMsg();
        msg.setPackager(parser.getPackager());
        msg.setMTI(request.getMti());
        boolean hasExplicitUiFields = request.getFields() != null && !request.getFields().isEmpty();

        Set<Integer> requestBits = new LinkedHashSet<>();
        if (config.getBitmap() != null && config.getBitmap().getRequestBits() != null) {
            requestBits.addAll(config.getBitmap().getRequestBits());
        }

        // In simulator UI flow, explicit request fields must be authoritative.
        // If UI provides any fields, skip config-driven request prefill entirely.
        if (!hasExplicitUiFields && config.getRequestFields() != null) {
            for (FieldConfig field : config.getRequestFields()) {
                if (!requestBits.isEmpty() && !requestBits.contains(field.getField())) {
                    continue;
                }
                String value = fieldValueResolver.resolve(field, msg);
                if (value != null && !value.isBlank()) {
                    msg.set(field.getField(), value);
                }
            }
        }

        // explicit request payload overrides profile-generated values
        if (request.getFields() != null) {
            boolean hex = request.isHexFieldValues();
            request.getFields().forEach((fieldStr, value) -> {
                try {
                    int field = Integer.parseInt(fieldStr);
                    if (hex && value != null && value.matches("[0-9A-Fa-f]+") && value.length() % 2 == 0) {
                        msg.set(field, ISOUtil.hex2byte(value));
                    } else {
                        msg.set(field, value);
                    }
                } catch (Exception ignored) {
                    // per-field failure should not abort build
                }
            });
        }
        return msg;
    }

    /**
     * Build an outgoing request message using the profile's clientDefaultFields.
     * The profile defines which fields to show the user and what default values to use.
     * Explicit UI fields always override profile defaults.
     */
    private ISOMsg buildFromProfile(SimulatorRequest request, MtiProfile profile) throws Exception {
        ISOMsg msg = new ISOMsg();
        msg.setPackager(parser.getPackager());
        msg.setMTI(request.getMti());

        boolean hasExplicitUiFields = request.getFields() != null && !request.getFields().isEmpty();

        // 1. Pre-populate from profile clientDefaultFields (when no explicit UI input)
        if (!hasExplicitUiFields && profile.getClientDefaultFields() != null) {
            for (FieldConfig field : profile.getClientDefaultFields()) {
                // Only set fields declared in mandatoryRequestBits or optionalRequestBits
                boolean inMandatory = profile.getMandatoryRequestBits() != null
                        && profile.getMandatoryRequestBits().contains(field.getField());
                boolean inOptional = profile.getOptionalRequestBits() != null
                        && profile.getOptionalRequestBits().contains(field.getField());
                if (!inMandatory && !inOptional) continue;

                String value = fieldValueResolver.resolve(field, msg);
                if (value != null && !value.isBlank()) {
                    msg.set(field.getField(), value);
                }
            }
        }

        // 2. Explicit request payload overrides profile defaults
        if (request.getFields() != null) {
            boolean hex = request.isHexFieldValues();
            request.getFields().forEach((field, value) -> {
                try {
                    if (hex && value != null && value.matches("[0-9A-Fa-f]+") && value.length() % 2 == 0) {
                        msg.set(field, org.jpos.iso.ISOUtil.hex2byte(value));
                    } else {
                        msg.set(field, value);
                    }
                } catch (Exception ignored) {
                }
            });
        }
        return msg;
    }

    private ISOMsg buildFromRaw(SimulatorRequest request) throws Exception {
        ISOMsg msg = new ISOMsg();
        msg.setPackager(parser.getPackager());
        msg.setMTI(request.getMti());
        if (request.getFields() != null) {
            boolean hex = request.isHexFieldValues();
            request.getFields().forEach((field, value) -> {
                try {
                    if (hex && value != null && value.matches("[0-9A-Fa-f]+") && value.length() % 2 == 0) {
                        msg.set(field, ISOUtil.hex2byte(value));
                    } else {
                        msg.set(field, value);
                    }
                } catch (Exception ignored) {
                }
            });
        }
        return msg;
    }
}

