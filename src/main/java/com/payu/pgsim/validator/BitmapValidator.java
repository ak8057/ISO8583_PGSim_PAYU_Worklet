package com.payu.pgsim.validator;

import com.payu.pgsim.model.BitmapConfig;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class BitmapValidator {

    public void validate(ISOMsg msg, BitmapConfig bitmap) {

        if (bitmap == null) {
            throw new IsoValidationException("Bitmap configuration missing");
        }

        List<Integer> mandatoryBits = bitmap.getMandatoryBits();
        List<Integer> optionalBits = bitmap.getOptionalBits();
        List<Integer> requestBits = bitmap.getRequestBits();

        Set<Integer> mustHave = new HashSet<>();
        Set<Integer> allowed = new HashSet<>();

        if (mandatoryBits != null && !mandatoryBits.isEmpty()) {
            mustHave.addAll(mandatoryBits);
            allowed.addAll(mandatoryBits);
            if (optionalBits != null) {
                allowed.addAll(optionalBits);
            }
        } else {
            if (requestBits == null || requestBits.isEmpty()) {
                throw new IsoValidationException("Bitmap configuration missing");
            }
            mustHave.addAll(requestBits);
            allowed.addAll(requestBits);
        }

        for (Integer field : mustHave) {
            if (!msg.hasField(field)) {
                throw new IsoValidationException("Missing mandatory bitmap field: " + field);
            }
        }

        for (int i = 2; i <= 128; i++) {
            if (msg.hasField(i) && !allowed.contains(i)) {
                throw new IsoValidationException("Unexpected field (not in bitmap): " + i);
            }
        }

        boolean hasSecondaryField = false;
        for (int i = 65; i <= 128; i++) {
            if (msg.hasField(i)) {
                hasSecondaryField = true;
                break;
            }
        }

        if (bitmap.isSecondaryBitmap() && !hasSecondaryField) {
            throw new IsoValidationException("Secondary bitmap expected but no fields > 64 present");
        }

        if (!bitmap.isSecondaryBitmap() && hasSecondaryField) {
            throw new IsoValidationException("Unexpected secondary bitmap fields present");
        }
    }
}
