package com.payu.pgsim.validator;

import com.payu.pgsim.model.BitmapConfig;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BitmapValidatorTest {

    private final BitmapValidator validator = new BitmapValidator();

    @Test
    void brdMode_mandatoryOnly_succeeds() {
        ISOMsg msg = new ISOMsg();
        msg.set(2, "4111111111111111");
        BitmapConfig bm = new BitmapConfig();
        bm.setMandatoryBits(List.of(2));
        bm.setOptionalBits(List.of(3));
        bm.setRequestBits(List.of(2, 3));
        bm.setSecondaryBitmap(false);
        assertDoesNotThrow(() -> validator.validate(msg, bm));
    }

    @Test
    void brdMode_mandatoryAndOptional_succeeds() {
        ISOMsg msg = new ISOMsg();
        msg.set(2, "4111111111111111");
        msg.set(3, "000000");
        BitmapConfig bm = new BitmapConfig();
        bm.setMandatoryBits(List.of(2));
        bm.setOptionalBits(List.of(3));
        bm.setRequestBits(List.of(2, 3));
        bm.setSecondaryBitmap(false);
        assertDoesNotThrow(() -> validator.validate(msg, bm));
    }

    @Test
    void brdMode_missingMandatory_fails() {
        ISOMsg msg = new ISOMsg();
        msg.set(3, "000000");
        BitmapConfig bm = new BitmapConfig();
        bm.setMandatoryBits(List.of(2));
        bm.setOptionalBits(List.of(3));
        bm.setRequestBits(List.of(2, 3));
        bm.setSecondaryBitmap(false);
        assertThrows(IsoValidationException.class, () -> validator.validate(msg, bm));
    }

    @Test
    void brdMode_unexpectedField_fails() {
        ISOMsg msg = new ISOMsg();
        msg.set(2, "4111111111111111");
        msg.set(4, "x");
        BitmapConfig bm = new BitmapConfig();
        bm.setMandatoryBits(List.of(2));
        bm.setOptionalBits(List.of(3));
        bm.setRequestBits(List.of(2, 3, 4));
        bm.setSecondaryBitmap(false);
        assertThrows(IsoValidationException.class, () -> validator.validate(msg, bm));
    }

    @Test
    void legacyMode_allRequestBitsMandatory() {
        ISOMsg msg = new ISOMsg();
        msg.set(2, "4111111111111111");
        BitmapConfig bm = new BitmapConfig();
        bm.setMandatoryBits(null);
        bm.setOptionalBits(null);
        bm.setRequestBits(List.of(2, 3));
        bm.setSecondaryBitmap(false);
        assertThrows(IsoValidationException.class, () -> validator.validate(msg, bm));

        msg.set(3, "000000");
        assertDoesNotThrow(() -> validator.validate(msg, bm));
    }

    @Test
    void secondaryBitmap_expectedWhenFieldGt64() {
        ISOMsg msg = new ISOMsg();
        msg.set(2, "4111111111111111");
        msg.set(65, "01");
        BitmapConfig bm = new BitmapConfig();
        bm.setMandatoryBits(List.of(2, 65));
        bm.setRequestBits(List.of(2, 65));
        bm.setSecondaryBitmap(true);
        assertDoesNotThrow(() -> validator.validate(msg, bm));
    }

    @Test
    void secondaryBitmap_mismatch_fails() {
        ISOMsg msg = new ISOMsg();
        msg.set(2, "4111111111111111");
        msg.set(65, "01");
        BitmapConfig bm = new BitmapConfig();
        bm.setMandatoryBits(List.of(2, 65));
        bm.setRequestBits(List.of(2, 65));
        bm.setSecondaryBitmap(false);
        assertThrows(IsoValidationException.class, () -> validator.validate(msg, bm));
    }
}
