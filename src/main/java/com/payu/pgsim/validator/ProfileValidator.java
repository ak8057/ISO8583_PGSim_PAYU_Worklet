package com.payu.pgsim.validator;

import com.payu.pgsim.model.FieldConfig;
import com.payu.pgsim.model.MtiProfile;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates an incoming ISO 8583 request against an {@link MtiProfile}.
 *
 * Validation order:
 *  1. Bitmap check – mandatory bits present, no unexpected bits
 *  2. Secondary bitmap check (if profile requires it)
 *  3. Per-field checks – mandatory, length, type
 *
 * All violations result in an {@link IsoValidationException} so they are
 * handled by the existing error path in {@link com.payu.pgsim.handler.MessageHandler}.
 */
@Component
public class ProfileValidator {

    /**
     * Validates an incoming request message against the profile's request-side rules.
     *
     * @param msg     the parsed ISO 8583 request message
     * @param profile the profile matching this request's MTI
     * @throws IsoValidationException on any validation failure
     */
    public void validate(ISOMsg msg, MtiProfile profile) {
        if (profile == null) {
            throw new IsoValidationException("No profile available for validation");
        }

        validateBitmap(msg, profile);
        validateFields(msg, profile);
    }

    // ── Bitmap validation ─────────────────────────────────────────────────────

    private void validateBitmap(ISOMsg msg, MtiProfile profile) {
        List<Integer> mandatory = profile.getMandatoryRequestBits();
        List<Integer> optional  = profile.getOptionalRequestBits();

        // Build "must have" and "allowed" sets
        Set<Integer> mustHave = new HashSet<>();
        Set<Integer> allowed  = new HashSet<>();

        if (mandatory != null) {
            mustHave.addAll(mandatory);
            allowed.addAll(mandatory);
        }
        if (optional != null) {
            allowed.addAll(optional);
        }

        // If neither mandatory nor optional bits are configured, skip bitmap enforcement
        if (mustHave.isEmpty() && allowed.isEmpty()) {
            return;
        }

        // 1. Check mandatory bits are present
        for (Integer bit : mustHave) {
            if (!msg.hasField(bit)) {
                throw new IsoValidationException(
                        "Profile [" + profile.getProfileId() + "]: Missing mandatory bitmap field DE" + bit);
            }
        }

        // 2. Check no unexpected bits
        for (int i = 2; i <= 128; i++) {
            if (msg.hasField(i) && !allowed.contains(i)) {
                throw new IsoValidationException(
                        "Profile [" + profile.getProfileId() + "]: Unexpected field DE" + i
                                + " (not declared in mandatory or optional bits)");
            }
        }

        // 3. Secondary bitmap check
        boolean hasSecondaryField = false;
        for (int i = 65; i <= 128; i++) {
            if (msg.hasField(i)) {
                hasSecondaryField = true;
                break;
            }
        }
        if (profile.isSecondaryBitmap() && !hasSecondaryField) {
            throw new IsoValidationException(
                    "Profile [" + profile.getProfileId() + "]: Secondary bitmap expected but no DE 65-128 present");
        }
        if (!profile.isSecondaryBitmap() && hasSecondaryField) {
            throw new IsoValidationException(
                    "Profile [" + profile.getProfileId() + "]: Unexpected secondary bitmap fields (DE 65-128) present");
        }
    }

    // ── Per-field validation ──────────────────────────────────────────────────

    private void validateFields(ISOMsg msg, MtiProfile profile) {
        List<FieldConfig> fields = profile.getRequestFields();
        if (fields == null || fields.isEmpty()) {
            return;
        }

        for (FieldConfig fieldDef : fields) {
            int de = fieldDef.getField();

            // Mandatory check
            if (fieldDef.isMandatory() && !msg.hasField(de)) {
                throw new IsoValidationException(
                        "Profile [" + profile.getProfileId() + "]: Missing mandatory field DE" + de);
            }

            if (!msg.hasField(de)) continue;

            String value = msg.getString(de);

            // Empty check
            if (value == null || value.isEmpty()) {
                throw new IsoValidationException(
                        "Profile [" + profile.getProfileId() + "]: Empty value for field DE" + de);
            }

            // Max length check
            int maxLen = fieldDef.getLength();
            if (fieldDef.getValidation() != null && fieldDef.getValidation().getMaxLength() != null) {
                int vMax = fieldDef.getValidation().getMaxLength();
                maxLen = maxLen > 0 ? Math.max(maxLen, vMax) : vMax;
            }
            if (maxLen > 0 && value.length() > maxLen) {
                throw new IsoValidationException(
                        "Profile [" + profile.getProfileId() + "]: Field DE" + de
                                + " exceeds max length " + maxLen);
            }

            // Min length check
            if (fieldDef.getValidation() != null && fieldDef.getValidation().getMinLength() != null) {
                if (value.length() < fieldDef.getValidation().getMinLength()) {
                    throw new IsoValidationException(
                            "Profile [" + profile.getProfileId() + "]: Field DE" + de
                                    + " below min length " + fieldDef.getValidation().getMinLength());
                }
            }

            // Type check
            if (fieldDef.getType() != null) {
                validateType(de, value, fieldDef.getType().toUpperCase(), profile.getProfileId());
            }
        }
    }

    private void validateType(int de, String value, String type, String profileId) {
        switch (type) {
            case "NUMERIC":
                if (!value.matches("\\d+")) {
                    throw new IsoValidationException(
                            "Profile [" + profileId + "]: Field DE" + de + " must be numeric");
                }
                break;
            case "PAN":
                if (!value.matches("\\d{13,19}")) {
                    throw new IsoValidationException(
                            "Profile [" + profileId + "]: Invalid PAN format for field DE" + de);
                }
                break;
            case "DATETIME":
                if (!value.matches("\\d{10}")) {
                    throw new IsoValidationException(
                            "Profile [" + profileId + "]: Invalid DATETIME format for field DE" + de
                                    + " (expected MMDDhhmmss)");
                }
                break;
            case "ALPHA":
                if (!value.matches("[A-Za-z]+")) {
                    throw new IsoValidationException(
                            "Profile [" + profileId + "]: Field DE" + de + " must be alphabetic");
                }
                break;
            default:
                // Unknown types are ignored (safe fallback)
                break;
        }
    }
}
