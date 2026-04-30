package com.payu.pgsim.generator;

import com.payu.pgsim.engine.RuleEngine;
import com.payu.pgsim.engine.ScenarioEngine;
import com.payu.pgsim.engine.ScenarioResult;
import com.payu.pgsim.generator.field.FieldValueResolver;
import com.payu.pgsim.handler.ScenarioTimeoutException;
import com.payu.pgsim.model.FieldConfig;
import com.payu.pgsim.model.FieldMode;
import com.payu.pgsim.model.MessageTypeConfig;
import com.payu.pgsim.service.MtiRouter;

import lombok.RequiredArgsConstructor;

import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ResponseGenerator {

    private static final Logger log = LoggerFactory.getLogger(ResponseGenerator.class);

    private final RuleEngine ruleEngine;
    private final ScenarioEngine scenarioEngine;
    private final MtiRouter mtiRouter;
    private final FieldValueResolver fieldValueResolver;

    private final Random random = new Random();

    public ISOMsg generateResponse(ISOMsg request,
            MessageTypeConfig config) throws Exception {

        if (config == null) {
            throw new RuntimeException("No configuration found for MTI: " + request.getMTI());
        }

        // Apply scenario (delay/timeout simulation)
        ScenarioResult scenarioResult = scenarioEngine.applyScenario(config.getScenario());
        if (scenarioResult == ScenarioResult.TIMEOUT) {
            throw new ScenarioTimeoutException("Scenario requested TIMEOUT");
        }

        ISOMsg response = new ISOMsg();

        // Copy packager
        response.setPackager(request.getPackager());

        // Determine response MTI
        String responseMti = config.getResponseMti() != null
                ? config.getResponseMti()
                : mtiRouter.getResponseMti(request.getMTI());

        if (responseMti == null) {
            throw new RuntimeException("Unable to resolve response MTI for request MTI: " + request.getMTI());
        }

        log.info("Response MTI: {}", responseMti);

        response.setMTI(responseMti);

        String requestMti = request.getMTI();
        boolean networkMessage = "0800".equals(requestMti) || "0820".equals(requestMti);
        String de70 = request.hasField(70) ? request.getString(70) : null;

        // Validate DE70 for network management messages.
        if (networkMessage) {
            if (de70 == null || (!"001".equals(de70) && !"002".equals(de70) && !"301".equals(de70))) {
                throw new RuntimeException("Invalid or missing DE70 for network message");
            }

            // Echo mode: 0800 + DE70=301 returns all request fields back.
            if ("0800".equals(requestMti) && "301".equals(de70)) {
                for (int i = 2; i <= 128; i++) {
                    if (request.hasField(i)) {
                        response.set(i, request.getString(i));
                    }
                }
                response.set(39, "00");
                return response;
            }
        }

        // Preserve trace fields
        copyRequestField(request, response, 11); // STAN
        copyRequestField(request, response, 37); // RRN

        // Network management code must be echoed in network responses.
        if (networkMessage && request.hasField(70)) {
            response.set(70, request.getString(70));
        }

        /*
         * -------------------------------------------------
         * Apply UI configured response fields
         * --------------------------------------------------
         */

        if (config.getResponseFields() != null) {

            for (FieldConfig field : config.getResponseFields()) {

                try {

                    int fieldNumber = field.getField();

                    String value = fieldValueResolver.resolve(field, request);

                    // Never overwrite with null/blank values.
                    if (value == null || value.trim().isEmpty()) {

                        // Safe fallback: if config is blank and request carries this field,
                        // reuse request value instead of setting empty response data.
                        if ((field.getValue() == null || field.getValue().trim().isEmpty())
                                && request.hasField(fieldNumber)) {

                            String requestValue = request.getString(fieldNumber);
                            if (requestValue != null && !requestValue.trim().isEmpty()) {
                                log.info("Applying fallback request field: {} = {}", fieldNumber, requestValue);
                                response.set(fieldNumber, requestValue);
                            } else {
                                log.info("Skipping response field {} (resolved/request value blank)", fieldNumber);
                            }
                        } else {
                            log.info("Skipping response field {} (resolved value blank)", fieldNumber);
                        }
                        continue;
                    }

                    log.info("Applying configured response field: {} = {}", fieldNumber, value);

                    response.set(fieldNumber, value);

                } catch (Exception e) {

                    log.warn("Error applying response field: {}", field.getField(), e);

                }

            }
        }

        /*
         * -------------------------------------------------
         * Apply default fields (fallback values)
         * --------------------------------------------------
         */

        if (config.getDefaultFields() != null) {

            for (Map.Entry<Integer, String> entry : config.getDefaultFields().entrySet()) {

                try {

                    int field = entry.getKey();

                    String value = resolveValue(entry.getValue(), request);

                    log.info("Applying default field: {} = {}", field, value);

                    response.set(field, value);

                } catch (Exception e) {

                    log.warn("Error applying default field: {}", entry.getKey(), e);

                }

            }
        }

        /*
         * -------------------------------------------------
         * Apply rules (override response code)
         * --------------------------------------------------
         */

        String responseCode = ruleEngine.evaluate(request, config.getRules());

        if (responseCode != null) {

            log.info("Rule engine selected response code: {}", responseCode);

            response.set(39, responseCode);

        }

        // =========================
        // ENFORCE RESPONSE BITMAP
        // =========================

        if (config.getBitmap() != null && config.getBitmap().getResponseBits() != null) {

            Set<Integer> allowed = new HashSet<>(config.getBitmap().getResponseBits());

            for (int i = 2; i <= 128; i++) {
                if (response.hasField(i) && !allowed.contains(i)) {
                    response.unset(i); // REMOVE unwanted field
                }
            }
        }

        // Ensure mandatory response code
        if (!response.hasField(39)) {
            response.set(39, "96");
        }

        return response;
    }

    private void copyRequestField(ISOMsg request,
            ISOMsg response,
            int field) {

        try {

            if (request.hasField(field)) {

                response.set(field,
                        request.getString(field));

            }

        } catch (Exception e) {

            log.warn("Error copying request field: {}", field, e);

        }
    }

    private String resolveValue(String value,
            ISOMsg request) {

        try {

            switch (value) {

                case "${RRN}":
                    return generateRRN();

                case "${DATETIME}":
                    return LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("MMddHHmmss"));

                case "${DATE}":
                    return LocalDate.now()
                            .format(DateTimeFormatter.ofPattern("MMdd"));

                case "${TIME}":
                    return LocalTime.now()
                            .format(DateTimeFormatter.ofPattern("HHmmss"));

                case "${STAN}":
                    return generateSTAN();

                case "${PAN}":
                    return request.hasField(2) ? request.getString(2) : "";

                case "${AMOUNT}":
                    return request.hasField(4) ? request.getString(4) : "";

            }

            if (value.startsWith("${REQUEST_")) {

                int field = Integer.parseInt(
                        value.substring(10, value.length() - 1));

                if (request.hasField(field)) {

                    return request.getString(field);

                }
            }

        } catch (Exception e) {

            log.warn("Value resolution error: {}", value, e);

        }

        return value;
    }

    private String resolveField(FieldConfig fieldConfig, ISOMsg request) {

        try {

            boolean hasMode = fieldConfig.getMode() != null && !fieldConfig.getMode().trim().isEmpty();
            boolean hasValueType = fieldConfig.getValueType() != null && !fieldConfig.getValueType().trim().isEmpty();
            if (!hasMode && !hasValueType) {
                return resolveValue(fieldConfig.getValue(), request);
            }

            String modeStr = resolveModeString(fieldConfig);
            FieldMode mode;
            try {
                mode = FieldMode.valueOf(modeStr);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid mode: {} for field {}, falling back to STATIC", modeStr, fieldConfig.getField());
                mode = FieldMode.STATIC;
            }

            switch (mode) {

                case STATIC:
                    return fieldConfig.getValue() != null ? fieldConfig.getValue()
                            : fieldConfig.getTemplate();

                case DYNAMIC:
                    String dyn = fieldConfig.getDynamicType() != null && !fieldConfig.getDynamicType().isBlank()
                            ? fieldConfig.getDynamicType()
                            : fieldConfig.getValue();
                    return resolveDynamicValue(dyn);

                case FROM_REQUEST:
                    int src = resolveSourceFieldNumber(fieldConfig);
                    if (src <= 0) {
                        log.warn("FROM_REQUEST requires sourceField or numeric value for field {}", fieldConfig.getField());
                        return null;
                    }
                    if (request.hasField(src)) {
                        return request.getString(src);
                    }
                    return null;

                case TEMPLATE:
                    String tpl = fieldConfig.getTemplate() != null && !fieldConfig.getTemplate().isBlank()
                            ? fieldConfig.getTemplate()
                            : fieldConfig.getValue();
                    return resolveTemplate(tpl, request);

                default:
                    log.warn("Unknown mode: {} for field {}", mode, fieldConfig.getField());
                    return fieldConfig.getValue();
            }

        } catch (Exception e) {
            log.warn("Field resolution error for field {}: {}", fieldConfig.getField(), e);
        }

        return fieldConfig.getValue();
    }

    private int resolveSourceFieldNumber(FieldConfig fieldConfig) {
        if (fieldConfig.getSourceField() != null && fieldConfig.getSourceField() > 0) {
            return fieldConfig.getSourceField();
        }
        if (fieldConfig.getValue() != null && isNumeric(fieldConfig.getValue())) {
            return Integer.parseInt(fieldConfig.getValue().trim());
        }
        return -1;
    }

    private String resolveModeString(FieldConfig fieldConfig) {
        if (fieldConfig.getMode() != null && !fieldConfig.getMode().trim().isEmpty()) {
            return fieldConfig.getMode().toUpperCase().trim();
        }
        if (fieldConfig.getValueType() != null && !fieldConfig.getValueType().trim().isEmpty()) {
            return fieldConfig.getValueType().toUpperCase().trim();
        }
        return "STATIC";
    }

    private boolean isNumeric(String str) {
        if (str == null || str.trim().isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(str.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String resolveDynamicValue(String dynamicType) {

        try {
            switch (dynamicType) {
                case "DATE":
                    return LocalDate.now().format(DateTimeFormatter.ofPattern("MMdd"));
                case "TIME":
                    return LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
                case "DATETIME":
                    return LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss"));
                case "STAN":
                    return generateSTAN();
                case "RRN":
                    return generateRRN();
                default:
                    return null;
            }
        } catch (Exception e) {
            log.warn("Error resolving dynamic value: {}", dynamicType, e);
            return null;
        }
    }

    private String resolveTemplate(String template, ISOMsg request) {

        String result = template;

        try {
            // Replace ${DATE}
            result = result.replace("${DATE}", LocalDate.now().format(DateTimeFormatter.ofPattern("MMdd")));

            // Replace ${TIME}
            result = result.replace("${TIME}", LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss")));

            // Replace ${DATETIME}
            result = result.replace("${DATETIME}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")));

            // Replace ${STAN}
            result = result.replace("${STAN}", generateSTAN());

            // Replace ${RRN}
            result = result.replace("${RRN}", generateRRN());

            if (request.hasField(2)) {
                result = result.replace("${PAN}", request.getString(2));
            } else {
                result = result.replace("${PAN}", "");
            }
            if (request.hasField(4)) {
                result = result.replace("${AMOUNT}", request.getString(4));
            } else {
                result = result.replace("${AMOUNT}", "");
            }

            // Replace ${REQUEST_nn} patterns
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\$\\{REQUEST_(\\d+)\\}");
            java.util.regex.Matcher matcher = pattern.matcher(result);

            while (matcher.find()) {
                int fieldNum = Integer.parseInt(matcher.group(1));
                if (request.hasField(fieldNum)) {
                    String fieldValue = request.getString(fieldNum);
                    result = result.replace("${REQUEST_" + fieldNum + "}", fieldValue != null ? fieldValue : "");
                } else {
                    result = result.replace("${REQUEST_" + fieldNum + "}", "");
                }
            }

        } catch (Exception e) {
            log.warn("Error resolving template: {}", template, e);
        }

        return result;
    }

    private String generateSTAN() {

        return String.valueOf(
                random.nextInt(900000) + 100000);
    }

    private String generateRRN() {

        long base = 100000000000L;

        long rrn = base + random.nextInt(900000000);

        return String.valueOf(rrn);
    }
}