package com.payu.pgsim.brd;

import com.payu.pgsim.brd.dto.BrdFieldConfig;
import com.payu.pgsim.brd.dto.BrdMessageTypeConfig;
import com.payu.pgsim.brd.dto.BrdRequestConfig;
import com.payu.pgsim.brd.dto.BrdResponseConfig;
import com.payu.pgsim.model.MessageTypeConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrdConfigMapperTest {

    private final BrdConfigMapper mapper = new BrdConfigMapper();

    @Test
    void toInternal_buildsRequestBitsUnionAndDefaultDe39() {
        BrdMessageTypeConfig brd = new BrdMessageTypeConfig();
        brd.setMti("0100");
        brd.setDescription("Auth");

        BrdRequestConfig req = new BrdRequestConfig();
        req.setMandatoryBits(List.of(2, 3));
        req.setOptionalBits(List.of(4));
        BrdFieldConfig f2 = new BrdFieldConfig();
        f2.setFieldNumber(2);
        f2.setFieldName("PAN");
        f2.setDataType("NUMERIC");
        f2.setValueType("FROM_REQUEST");
        req.setFieldConfigs(List.of(f2));

        BrdResponseConfig res = new BrdResponseConfig();
        res.setResponseMti("0110");
        res.setResponseBits(List.of(39));
        res.setDefaultResponseCode("00");
        res.setFieldConfigs(List.of());

        brd.setRequestConfig(req);
        brd.setResponseConfig(res);

        MessageTypeConfig internal = mapper.toInternal(brd);
        assertNotNull(internal.getBitmap());
        assertTrue(internal.getBitmap().getRequestBits().containsAll(List.of(2, 3, 4)));
        assertTrue(internal.getResponseFields().stream().anyMatch(f -> f.getField() == 39));
        assertEquals("00", internal.getResponseFields().stream()
                .filter(f -> f.getField() == 39)
                .findFirst()
                .orElseThrow()
                .getValue());
    }

    @Test
    void roundTrip_preservesMtiAndBits() {
        BrdMessageTypeConfig brd = new BrdMessageTypeConfig();
        brd.setMti("0200");
        BrdRequestConfig req = new BrdRequestConfig();
        req.setMandatoryBits(List.of(2));
        req.setOptionalBits(List.of(3));
        req.setFieldConfigs(List.of());
        BrdResponseConfig res = new BrdResponseConfig();
        res.setResponseMti("0210");
        res.setResponseBits(List.of(39));
        res.setFieldConfigs(List.of());
        brd.setRequestConfig(req);
        brd.setResponseConfig(res);

        BrdMessageTypeConfig back = mapper.toBrd(mapper.toInternal(brd));
        assertEquals("0200", back.getMti());
        assertEquals(List.of(2), back.getRequestConfig().getMandatoryBits());
        assertEquals(List.of(3), back.getRequestConfig().getOptionalBits());
        assertEquals("0210", back.getResponseConfig().getResponseMti());
    }
}
