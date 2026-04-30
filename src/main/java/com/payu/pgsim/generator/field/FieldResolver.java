package com.payu.pgsim.generator.field;

import com.payu.pgsim.model.FieldConfig;
import com.payu.pgsim.model.FieldMode;
import org.jpos.iso.ISOMsg;

public interface FieldResolver {
    boolean supports(FieldMode mode);

    String resolve(FieldConfig fieldConfig, ISOMsg sourceMessage);
}

