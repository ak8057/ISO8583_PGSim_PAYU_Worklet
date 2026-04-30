package com.payu.pgsim.generator.field;

import com.payu.pgsim.model.FieldConfig;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FieldValueResolverTest {

    private FieldValueResolver resolver;

    @BeforeEach
    void setUp() {
        DynamicTokenGenerator generator = new DynamicTokenGenerator();
        resolver = new FieldValueResolver(List.of(
                new StaticFieldResolver(),
                new FromRequestFieldResolver(),
                new DynamicFieldResolver(generator),
                new TemplateFieldResolver(generator)
        ));
    }

    @Test
    void resolvesStaticField() {
        FieldConfig cfg = new FieldConfig();
        cfg.setValueType("STATIC");
        cfg.setValue("00");

        assertEquals("00", resolver.resolve(cfg, new ISOMsg()));
    }

    @Test
    void resolvesFromRequestField() throws Exception {
        ISOMsg request = new ISOMsg();
        request.set(11, "123456");

        FieldConfig cfg = new FieldConfig();
        cfg.setValueType("FROM_REQUEST");
        cfg.setSourceField(11);

        assertEquals("123456", resolver.resolve(cfg, request));
    }

    @Test
    void resolvesDynamicStanField() {
        FieldConfig cfg = new FieldConfig();
        cfg.setValueType("DYNAMIC");
        cfg.setDynamicType("STAN");

        String value = resolver.resolve(cfg, new ISOMsg());
        assertNotNull(value);
        assertEquals(6, value.length());
        assertTrue(value.matches("\\d{6}"));
    }
}

