package com.payu.pgsim.generator.field;

import com.payu.pgsim.model.FieldConfig;
import com.payu.pgsim.model.FieldMode;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Component;

@Component
public class StaticFieldResolver implements FieldResolver {
    @Override
    public boolean supports(FieldMode mode) {
        return mode == FieldMode.STATIC;
    }

    @Override
    public String resolve(FieldConfig fieldConfig, ISOMsg sourceMessage) {
        if (fieldConfig.getValue() != null && !fieldConfig.getValue().isBlank()) {
            return fieldConfig.getValue();
        }
        return fieldConfig.getTemplate();
    }
}

