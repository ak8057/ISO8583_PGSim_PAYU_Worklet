package com.payu.pgsim.generator.field;

import com.payu.pgsim.model.FieldConfig;
import com.payu.pgsim.model.FieldMode;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FieldValueResolver {

    private final List<FieldResolver> resolvers;

    public FieldValueResolver(List<FieldResolver> resolvers) {
        this.resolvers = resolvers;
    }

    public String resolve(FieldConfig fieldConfig, ISOMsg sourceMessage) {
        FieldMode mode = resolveMode(fieldConfig);
        for (FieldResolver resolver : resolvers) {
            if (resolver.supports(mode)) {
                return resolver.resolve(fieldConfig, sourceMessage);
            }
        }
        return fieldConfig.getValue();
    }

    private FieldMode resolveMode(FieldConfig fieldConfig) {
        if (fieldConfig.getValueType() != null && !fieldConfig.getValueType().isBlank()) {
            return FieldMode.fromString(fieldConfig.getValueType());
        }
        if (fieldConfig.getMode() != null && !fieldConfig.getMode().isBlank()) {
            return FieldMode.fromString(fieldConfig.getMode());
        }
        return FieldMode.STATIC;
    }
}

