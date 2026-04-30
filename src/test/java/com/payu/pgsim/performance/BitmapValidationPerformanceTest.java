package com.payu.pgsim.performance;

import com.payu.pgsim.model.BitmapConfig;
import com.payu.pgsim.validator.BitmapValidator;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TR-5 smoke: coarse throughput guard (not a formal benchmark).
 */
class BitmapValidationPerformanceTest {

    @Test
    void repeatedValidationCompletesWithinReasonableTime() {
        BitmapValidator validator = new BitmapValidator();
        ISOMsg msg = new ISOMsg();
        msg.set(2, "4111111111111111");
        msg.set(3, "000000");
        BitmapConfig bm = new BitmapConfig();
        bm.setMandatoryBits(List.of(2));
        bm.setOptionalBits(List.of(3));
        bm.setRequestBits(List.of(2, 3));
        bm.setSecondaryBitmap(false);

        int n = 8000;
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            validator.validate(msg, bm);
        }
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        assertThat(ms).isLessThan(3000L);
    }
}
