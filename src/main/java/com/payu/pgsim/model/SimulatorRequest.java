package com.payu.pgsim.model;

import lombok.Data;
import java.util.Map;

@Data
public class SimulatorRequest {

    private String mti;

    private Map<String,String> fields;

    /**
     * When true, each field value is interpreted as hex (e.g. PAN nibbles) and applied as binary to the ISOMsg.
     */
    private boolean hexFieldValues;
}