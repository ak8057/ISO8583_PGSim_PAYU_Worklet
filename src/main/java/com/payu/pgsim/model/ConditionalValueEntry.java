package com.payu.pgsim.model;

import lombok.Data;

/**
 * BRD §10.1 {@code conditionalValues} — stored for BRD JSON round-trip;
 * runtime DE39 conditions remain driven by {@link com.payu.pgsim.model.ResponseRule} where configured.
 */
@Data
public class ConditionalValueEntry {

    private String condition;
    private String value;
}
