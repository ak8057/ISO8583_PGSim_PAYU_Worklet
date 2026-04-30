package com.payu.pgsim.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class MessageTypeConfig {

    private String mti;

    /** BRD §10.1 human-readable label */
    private String description;

    private String responseMti;

    private BitmapConfig bitmap;

    /**
     * Outgoing-profile shorthand: root-level request bits for simulator message construction.
     * Normalized into {@code bitmap.requestBits} at runtime.
     */
    private List<Integer> requestBits;

    private List<FieldConfig> requestFields;

    /**
     * Outgoing-profile shorthand: root-level field configs for request construction.
     * Normalized into {@code requestFields} at runtime.
     */
    private List<FieldConfig> fieldConfigs;

    private List<FieldConfig> responseFields;

    private Map<Integer,String> defaultFields;

    private List<ResponseRule> rules;

    private ScenarioRule scenario;

}