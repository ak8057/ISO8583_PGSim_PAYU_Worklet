package com.payu.pgsim.validator;

import com.payu.pgsim.model.BitmapConfig;
import com.payu.pgsim.model.FieldConfig;
import com.payu.pgsim.model.MessageTypeConfig;
import com.payu.pgsim.model.ResponseRule;
import com.payu.pgsim.model.ValidationResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ConfigValidator {

    public void validate(MessageTypeConfig config) {
        if (config == null) {
            throw new IsoValidationException("Configuration is required");
        }

        if (config.getMti() == null || config.getMti().length() != 4) {
            throw new IsoValidationException("Invalid MTI");
        }

        if (config.getResponseMti() != null && config.getResponseMti().length() != 4) {
            throw new IsoValidationException("Invalid response MTI");
        }

        BitmapConfig bm = config.getBitmap();
        if (bm == null) {
            throw new IsoValidationException("bitmap is required");
        }
        validateBitmapBits(bm);
        validateRequestConsistency(config, bm);
        ensureNoDuplicateFields(config.getRequestFields(), "requestFields");
        ensureNoDuplicateFields(config.getResponseFields(), "responseFields");
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
            try {
                validateBitmapBits(bm);
                validateRequestConsistency(config, bm);
            } catch (IsoValidationException e) {
                errors.add(e.getMessage());
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

    private void ensureNoDuplicateFields(List<FieldConfig> fields, String label) {
        List<String> errors = new ArrayList<>();
        checkDuplicateFields(fields, label, errors);
        if (!errors.isEmpty()) {
            throw new IsoValidationException(errors.get(0));
        }
    }

    private void validateBitmapBits(BitmapConfig bm) {
        validateBitList("bitmap.requestBits", bm.getRequestBits());
        validateBitList("bitmap.responseBits", bm.getResponseBits());
        validateBitList("bitmap.mandatoryBits", bm.getMandatoryBits());
        validateBitList("bitmap.optionalBits", bm.getOptionalBits());

        Set<Integer> overlap = new HashSet<>(safeList(bm.getMandatoryBits()));
        overlap.retainAll(safeList(bm.getOptionalBits()));
        if (!overlap.isEmpty()) {
            throw new IsoValidationException("bitmap.mandatoryBits and optionalBits overlap: " + overlap);
        }

        boolean hasRequestBits = bm.getRequestBits() != null && !bm.getRequestBits().isEmpty();
        boolean hasMandatory = bm.getMandatoryBits() != null && !bm.getMandatoryBits().isEmpty();
        if (!hasRequestBits && !hasMandatory) {
            throw new IsoValidationException("bitmap must define requestBits or mandatoryBits (BRD)");
        }
    }

    private void validateRequestConsistency(MessageTypeConfig config, BitmapConfig bm) {
        Set<Integer> requestFieldBits = new HashSet<>();
        Set<Integer> requestMandatoryFieldBits = new HashSet<>();
        if (config.getRequestFields() != null) {
            for (FieldConfig field : config.getRequestFields()) {
                if (field == null) continue;
                int de = field.getField();
                if (de < 2 || de > 128) {
                    throw new IsoValidationException("requestFields contains invalid DE: " + de + " (must be 2..128)");
                }
                requestFieldBits.add(de);
                if (field.isMandatory()) {
                    requestMandatoryFieldBits.add(de);
                }
            }
        }

        Set<Integer> allowedRequestBits = new HashSet<>();
        if (bm.getMandatoryBits() != null && !bm.getMandatoryBits().isEmpty()) {
            allowedRequestBits.addAll(bm.getMandatoryBits());
            if (bm.getOptionalBits() != null) {
                allowedRequestBits.addAll(bm.getOptionalBits());
            }
        } else if (bm.getRequestBits() != null) {
            allowedRequestBits.addAll(bm.getRequestBits());
        }

        if (!allowedRequestBits.isEmpty()) {
            Set<Integer> undeclaredRequestFields = new HashSet<>(requestFieldBits);
            undeclaredRequestFields.removeAll(allowedRequestBits);
            if (!undeclaredRequestFields.isEmpty()) {
                throw new IsoValidationException("requestFields contain DEs not present in request bitmap: " + undeclaredRequestFields);
            }
        }

        if (bm.getMandatoryBits() != null && !bm.getMandatoryBits().isEmpty()) {
            Set<Integer> bitmapMandatory = new HashSet<>(bm.getMandatoryBits());
            if (!requestMandatoryFieldBits.equals(bitmapMandatory)) {
                Set<Integer> onlyInBitmap = new HashSet<>(bitmapMandatory);
                onlyInBitmap.removeAll(requestMandatoryFieldBits);
                Set<Integer> onlyInFields = new HashSet<>(requestMandatoryFieldBits);
                onlyInFields.removeAll(bitmapMandatory);
                throw new IsoValidationException(
                        "mandatory bitmap bits and mandatory requestFields must match; onlyInBitmap="
                                + onlyInBitmap + ", onlyInRequestFields=" + onlyInFields);
            }
        }

        if (bm.getResponseBits() != null && config.getResponseFields() != null) {
            Set<Integer> responseBits = new HashSet<>(bm.getResponseBits());
            Set<Integer> responseFieldBits = new HashSet<>();
            for (FieldConfig field : config.getResponseFields()) {
                if (field == null) continue;
                int de = field.getField();
                if (de < 2 || de > 128) {
                    throw new IsoValidationException("responseFields contains invalid DE: " + de + " (must be 2..128)");
                }
                responseFieldBits.add(de);
            }
            Set<Integer> undeclaredResponseFields = new HashSet<>(responseFieldBits);
            undeclaredResponseFields.removeAll(responseBits);
            if (!undeclaredResponseFields.isEmpty()) {
                throw new IsoValidationException("responseFields contain DEs not present in response bitmap: " + undeclaredResponseFields);
            }
        }
    }

    private void validateBitList(String label, List<Integer> bits) {
        if (bits == null) {
            return;
        }
        Set<Integer> seen = new HashSet<>();
        for (Integer bit : bits) {
            if (bit == null) {
                throw new IsoValidationException(label + " contains null bit");
            }
            if (bit < 2 || bit > 128) {
                throw new IsoValidationException(label + " contains invalid bit " + bit + " (must be 2..128)");
            }
            if (!seen.add(bit)) {
                throw new IsoValidationException(label + " contains duplicate bit " + bit);
            }
        }
    }

    private List<Integer> safeList(List<Integer> values) {
        return values == null ? Collections.emptyList() : values;
    }
}
