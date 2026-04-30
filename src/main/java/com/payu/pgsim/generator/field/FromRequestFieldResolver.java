package com.payu.pgsim.generator.field;

import com.payu.pgsim.model.FieldConfig;
import com.payu.pgsim.model.FieldMode;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Component;

@Component
public class FromRequestFieldResolver implements FieldResolver {
    @Override
    public boolean supports(FieldMode mode) {
        return mode == FieldMode.FROM_REQUEST;
    }

    @Override
    public String resolve(FieldConfig fieldConfig, ISOMsg sourceMessage) {
        if (sourceMessage == null) {
            return null;
        }
        Integer src = fieldConfig.getSourceField();
        if (src == null && fieldConfig.getValue() != null && fieldConfig.getValue().matches("\\d+")) {
            src = Integer.parseInt(fieldConfig.getValue());
        }
        if (src == null || src <= 0 || !sourceMessage.hasField(src)) {
            return null;
        }
        return sourceMessage.getString(src);
    }
}

