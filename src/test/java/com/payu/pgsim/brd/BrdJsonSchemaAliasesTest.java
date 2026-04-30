package com.payu.pgsim.brd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payu.pgsim.brd.dto.BrdConditionalValue;
import com.payu.pgsim.brd.dto.BrdFieldConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrdJsonSchemaAliasesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void fieldConfigAcceptsModeAliasForValueType() throws Exception {
        BrdFieldConfig f = mapper.readValue(
                "{\"fieldNumber\":2,\"mode\":\"TEMPLATE\",\"template\":\"${STAN}\"}",
                BrdFieldConfig.class);
        assertEquals("TEMPLATE", f.getValueType());
    }

    @Test
    void conditionalValueAcceptsWhenAlias() throws Exception {
        BrdConditionalValue v = mapper.readValue(
                "{\"when\":\"DE39=05\",\"value\":\"DECLINED\"}",
                BrdConditionalValue.class);
        assertEquals("DE39=05", v.getCondition());
        assertEquals("DECLINED", v.getValue());
    }
}
