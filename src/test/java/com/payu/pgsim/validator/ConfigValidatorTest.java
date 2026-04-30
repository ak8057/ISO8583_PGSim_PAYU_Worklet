package com.payu.pgsim.validator;

import com.payu.pgsim.model.BitmapConfig;
import com.payu.pgsim.model.MessageTypeConfig;
import com.payu.pgsim.model.ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigValidatorTest {

    private final ConfigValidator validator = new ConfigValidator();

    @Test
    void validMinimalStructure() {
        MessageTypeConfig c = new MessageTypeConfig();
        c.setMti("0100");
        c.setResponseMti("0110");
        BitmapConfig b = new BitmapConfig();
        b.setRequestBits(List.of(2, 3));
        b.setResponseBits(List.of(39));
        c.setBitmap(b);
        ValidationResult r = validator.validateDetailed(c);
        assertTrue(r.isValid(), r.getErrors().toString());
    }

    @Test
    void invalidMtiLength() {
        MessageTypeConfig c = new MessageTypeConfig();
        c.setMti("010");
        ValidationResult r = validator.validateDetailed(c);
        assertFalse(r.isValid());
    }

    @Test
    void missingBitmap() {
        MessageTypeConfig c = new MessageTypeConfig();
        c.setMti("0100");
        ValidationResult r = validator.validateDetailed(c);
        assertFalse(r.isValid());
    }
}
