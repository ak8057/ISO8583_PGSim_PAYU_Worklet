package com.payu.pgsim.validator;

import com.payu.pgsim.model.BitmapConfig;
import com.payu.pgsim.model.FieldConfig;
import com.payu.pgsim.model.MessageTypeConfig;
import com.payu.pgsim.model.ResponseRule;
import com.payu.pgsim.model.ValidationResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ConfigValidator {

    public void validate(MessageTypeConfig config) {

        if (config.getMti() == null || config.getMti().length() != 4) {
            throw new IsoValidationException("Invalid MTI");
        }

        if (config.getResponseMti() != null && config.getResponseMti().length() != 4) {
            throw new IsoValidationException("Invalid response MTI");
        }
    }

    /**
     * Structural validation for API (no live ISO message required).
     */
    public ValidationResult validateDetailed(MessageTypeConfig config) {
        List<String> errors = new ArrayList<>();
        if (config == null) {
            errors.add("configuration is null");
            return new ValidationResult(false, errors);
        }
        try {
            validate(config);
        } catch (IsoValidationException e) {
            errors.add(e.getMessage());
        }

        BitmapConfig bm = config.getBitmap();
        if (bm == null) {
            errors.add("bitmap is required");
        } else {
            boolean hasRequestBits = bm.getRequestBits() != null && !bm.getRequestBits().isEmpty();
            boolean hasMandatory = bm.getMandatoryBits() != null && !bm.getMandatoryBits().isEmpty();
            if (!hasRequestBits && !hasMandatory) {
                errors.add("bitmap must define requestBits or mandatoryBits (BRD)");
            }
        }

        checkDuplicateFields(config.getRequestFields(), "requestFields", errors);
        checkDuplicateFields(config.getResponseFields(), "responseFields", errors);

        if (config.getRules() != null) {
            for (ResponseRule rule : config.getRules()) {
                if (rule.getResponseCode() != null && !rule.getResponseCode().isBlank()) {
                    String rc = rule.getResponseCode().trim();
                    if (!rc.matches("\\d{2}")) {
                        errors.add("rule responseCode must be 2 digits: " + rc);
                    }
                }
            }
        }

        if (config.getResponseFields() != null) {
            for (FieldConfig f : config.getResponseFields()) {
                if (f.getField() == 39 && f.getValue() != null && !f.getValue().isBlank()) {
                    String v = f.getValue().trim();
                    if (!v.matches("\\d{2}") && !v.contains("${")) {
                        errors.add("response field 39 should be 2-digit code or template");
                    }
                }
            }
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    private void checkDuplicateFields(List<FieldConfig> fields, String label, List<String> errors) {
        if (fields == null) {
            return;
        }
        Set<Integer> seen = new HashSet<>();
        for (FieldConfig f : fields) {
            if (!seen.add(f.getField())) {
                errors.add(label + ": duplicate field " + f.getField());
            }
        }
    }
}
