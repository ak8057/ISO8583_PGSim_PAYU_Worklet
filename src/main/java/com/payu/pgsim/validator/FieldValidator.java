package com.payu.pgsim.validator;

import com.payu.pgsim.model.FieldConfig;
import com.payu.pgsim.model.MessageTypeConfig;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Component;

import com.payu.pgsim.model.FieldValidation;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class FieldValidator {

    public void validate(ISOMsg msg, MessageTypeConfig config) {

        if (config == null || config.getRequestFields() == null) {
            throw new IsoValidationException("No request field configuration defined");
        }

        // =========================
        // 1. BUILD ALLOWED FIELD SET
        // =========================
        Set<Integer> allowedFields = new HashSet<>();
        for (FieldConfig field : config.getRequestFields()) {
            allowedFields.add(field.getField());
        }

        // =========================
        // 2. CHECK UNEXPECTED FIELDS
        // =========================
        for (int i = 2; i <= 128; i++) {
            if (msg.hasField(i) && !allowedFields.contains(i)) {
                throw new IsoValidationException("Unexpected field: " + i);
            }
        }

        // =========================
        // 3. VALIDATE EXPECTED FIELDS
        // =========================
        for (FieldConfig field : config.getRequestFields()) {

            int f = field.getField();

            // -------------------------
            // Mandatory check
            // -------------------------
            if (field.isMandatory() && !msg.hasField(f)) {
                throw new IsoValidationException("Missing mandatory field: " + f);
            }

            if (!msg.hasField(f)) {
                continue;
            }

            String value = msg.getString(f);

            // -------------------------
            // Null / empty check
            // -------------------------
            if (value == null || value.isEmpty()) {
                throw new IsoValidationException("Empty value for field: " + f);
            }

            // -------------------------
            // Length check (max length)
            // -------------------------
            int maxLen = field.getLength();
            FieldValidation fv = field.getValidation();
            if (fv != null && fv.getMaxLength() != null) {
                maxLen = maxLen > 0 ? Math.max(maxLen, fv.getMaxLength()) : fv.getMaxLength();
            }
            if (maxLen > 0 && value.length() > maxLen) {
                throw new IsoValidationException("Field " + f + " exceeds max length");
            }
            if (fv != null && fv.getMinLength() != null && value.length() < fv.getMinLength()) {
                throw new IsoValidationException("Field " + f + " below min length");
            }
            if (fv != null && fv.getAllowedValues() != null && !fv.getAllowedValues().isEmpty()) {
                boolean ok = false;
                for (String allowed : fv.getAllowedValues()) {
                    if (allowed != null && allowed.equals(value)) {
                        ok = true;
                        break;
                    }
                }
                if (!ok) {
                    throw new IsoValidationException("Field " + f + " value not in allowed set");
                }
            }
            if (fv != null && fv.getPattern() != null && !fv.getPattern().isBlank()) {
                if (!Pattern.matches(fv.getPattern(), value)) {
                    throw new IsoValidationException("Field " + f + " does not match pattern");
                }
            }

            // =========================
            // 4. TYPE VALIDATION
            // =========================
            if (field.getType() != null) {

                String type = field.getType().toUpperCase();

                switch (type) {

                    case "NUMERIC":
                        if (!value.matches("\\d+")) {
                            throw new IsoValidationException("Field " + f + " must be numeric");
                        }
                        break;

                    case "PAN":
                        // 13–19 digits
                        if (!value.matches("\\d{13,19}")) {
                            throw new IsoValidationException("Invalid PAN format for field " + f);
                        }
                        break;

                    case "DATETIME":
                        // MMDDhhmmss → 10 digits
                        if (!value.matches("\\d{10}")) {
                            throw new IsoValidationException("Invalid DATETIME format for field " + f);
                        }
                        break;

                    case "ALPHA":
                        if (!value.matches("[A-Za-z]+")) {
                            throw new IsoValidationException("Field " + f + " must be alphabetic");
                        }
                        break;

                    default:
                        // Unknown types are ignored (safe fallback)
                        break;
                }
            }
        }
    }
}