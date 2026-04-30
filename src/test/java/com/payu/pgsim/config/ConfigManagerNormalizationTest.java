package com.payu.pgsim.config;

import com.payu.pgsim.model.BitmapConfig;
import com.payu.pgsim.model.FieldConfig;
import com.payu.pgsim.model.MessageTypeConfig;
import com.payu.pgsim.validator.ConfigValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ConfigManagerNormalizationTest {

    @Test
    void importDerivesRequestBitsFromRequestFieldsWhenMissing() {
        ConfigValidator validator = mock(ConfigValidator.class);
        RuntimeConfigurationPersistence persistence = mock(RuntimeConfigurationPersistence.class);
        ConfigManager manager = new ConfigManager(validator, persistence);

        MessageTypeConfig cfg = new MessageTypeConfig();
        cfg.setMti("0200");
        BitmapConfig bitmap = new BitmapConfig();
        cfg.setBitmap(bitmap);

        FieldConfig f3 = new FieldConfig();
        f3.setField(3);
        FieldConfig f11 = new FieldConfig();
        f11.setField(11);
        cfg.setRequestFields(List.of(f3, f11));

        manager.importMessageTypes(List.of(cfg));

        MessageTypeConfig saved = manager.getConfig("0200");
        assertEquals(List.of(3, 11), saved.getBitmap().getRequestBits());
        verify(validator, atLeastOnce()).validate(any(MessageTypeConfig.class));
        verify(persistence, atLeastOnce()).persistIfEnabled(anyList());
    }
}

