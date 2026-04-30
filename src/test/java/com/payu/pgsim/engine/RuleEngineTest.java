package com.payu.pgsim.engine;

import com.payu.pgsim.model.ResponseRule;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RuleEngineTest {

    private final RuleEngine engine = new RuleEngine();

    @Test
    void greaterThanMatchesAmount() throws ISOException {
        ISOMsg msg = new ISOMsg();
        msg.set(4, "20000");
        ResponseRule rule = new ResponseRule();
        rule.setRuleId("r1");
        rule.setField(4);
        rule.setOperator(">");
        rule.setValue("10000");
        rule.setResponseCode("51");
        assertEquals("51", engine.evaluate(msg, List.of(rule)));
    }

    @Test
    void startsWithPan() throws ISOException {
        ISOMsg msg = new ISOMsg();
        msg.set(2, "4111111111111");
        ResponseRule rule = new ResponseRule();
        rule.setRuleId("r2");
        rule.setField(2);
        rule.setOperator("startsWith");
        rule.setValue("4");
        rule.setResponseCode("05");
        assertEquals("05", engine.evaluate(msg, List.of(rule)));
    }

    @Test
    void noMatchReturnsNull() throws ISOException {
        ISOMsg msg = new ISOMsg();
        msg.set(4, "100");
        ResponseRule rule = new ResponseRule();
        rule.setRuleId("r3");
        rule.setField(4);
        rule.setOperator(">");
        rule.setValue("10000");
        rule.setResponseCode("51");
        assertNull(engine.evaluate(msg, List.of(rule)));
    }
}
