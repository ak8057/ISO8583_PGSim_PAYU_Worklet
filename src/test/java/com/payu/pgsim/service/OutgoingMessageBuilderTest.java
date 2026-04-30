package com.payu.pgsim.service;

import com.payu.pgsim.config.ConfigManager;
import com.payu.pgsim.generator.field.DynamicFieldResolver;
import com.payu.pgsim.generator.field.DynamicTokenGenerator;
import com.payu.pgsim.generator.field.FieldValueResolver;
import com.payu.pgsim.generator.field.FromRequestFieldResolver;
import com.payu.pgsim.generator.field.StaticFieldResolver;
import com.payu.pgsim.generator.field.TemplateFieldResolver;
import com.payu.pgsim.model.BitmapConfig;
import com.payu.pgsim.model.FieldConfig;
import com.payu.pgsim.model.MessageTypeConfig;
import com.payu.pgsim.model.SimulatorRequest;
import com.payu.pgsim.parser.Iso8583Parser;
import com.payu.pgsim.service.ProfileService;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutgoingMessageBuilderTest {

    private ConfigManager configManager;
    private Iso8583Parser parser;
    private ProfileService profileService;
    private OutgoingMessageBuilder builder;

    @BeforeEach
    void setUp() {
        configManager = mock(ConfigManager.class);
        parser = mock(Iso8583Parser.class);
        profileService = mock(ProfileService.class);
        DynamicTokenGenerator generator = new DynamicTokenGenerator();
        FieldValueResolver resolver = new FieldValueResolver(List.of(
                new StaticFieldResolver(),
                new FromRequestFieldResolver(),
                new DynamicFieldResolver(generator),
                new TemplateFieldResolver(generator)
        ));
        builder = new OutgoingMessageBuilder(configManager, parser, resolver, profileService);
        when(parser.getPackager()).thenReturn(mock(GenericPackager.class));
    }

    @Test
    void buildsRequestFromProfileAndAllowsOverrides() throws Exception {
        MessageTypeConfig cfg = new MessageTypeConfig();
        cfg.setMti("0200");
        BitmapConfig bitmapConfig = new BitmapConfig();
        bitmapConfig.setRequestBits(List.of(3, 11));
        cfg.setBitmap(bitmapConfig);

        FieldConfig f3 = new FieldConfig();
        f3.setField(3);
        f3.setValueType("STATIC");
        f3.setValue("000000");

        FieldConfig f11 = new FieldConfig();
        f11.setField(11);
        f11.setValueType("DYNAMIC");
        f11.setDynamicType("STAN");
        cfg.setRequestFields(List.of(f3, f11));

        when(configManager.getConfig("0200")).thenReturn(cfg);

        SimulatorRequest req = new SimulatorRequest();
        req.setMti("0200");
        req.setFields(Map.of("3", "999999"));

        ISOMsg msg = builder.build(req);
        assertEquals("0200", msg.getMTI());
        assertEquals("999999", msg.getString(3));
        assertNotNull(msg.getString(11));
        assertEquals(6, msg.getString(11).length());
    }

    @Test
    void fallsBackToRawRequestWhenNoProfileFound() throws Exception {
        when(configManager.getConfig("0800")).thenReturn(null);
        SimulatorRequest req = new SimulatorRequest();
        req.setMti("0800");
        req.setFields(Map.of("70", "301"));

        ISOMsg msg = builder.build(req);
        assertEquals("0800", msg.getMTI());
        assertEquals("301", msg.getString(70));
    }
}
