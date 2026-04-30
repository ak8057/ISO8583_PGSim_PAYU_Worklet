package com.payu.pgsim.generator.field;

import com.payu.pgsim.model.FieldConfig;
import com.payu.pgsim.model.FieldMode;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Component;

@Component
public class DynamicFieldResolver implements FieldResolver {

    private final DynamicTokenGenerator dynamicTokenGenerator;

    public DynamicFieldResolver(DynamicTokenGenerator dynamicTokenGenerator) {
        this.dynamicTokenGenerator = dynamicTokenGenerator;
    }

    @Override
    public boolean supports(FieldMode mode) {
        return mode == FieldMode.DYNAMIC;
    }

    @Override
    public String resolve(FieldConfig fieldConfig, ISOMsg sourceMessage) {
        String key = fieldConfig.getDynamicType();
        if (key == null || key.isBlank()) {
            key = fieldConfig.getValue();
        }
        return dynamicTokenGenerator.generate(key);
    }
}

