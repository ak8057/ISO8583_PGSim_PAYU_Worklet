package com.payu.pgsim.model;

import lombok.Data;

@Data
public class FieldConfig {

    private int field;

    /** BRD §10.2 display name */
    private String fieldName;

    /** BRD §10.2 dataType e.g. LLVAR_NUMERIC */
    private String dataType;

    private String type;

    private String value;

    /** BRD: STATIC | TEMPLATE | FROM_REQUEST | DYNAMIC (mirrors {@link #mode}) */
    private String valueType;

    private String template;

    /** BRD: DATE | TIME | STAN | COUNTER | RRN | DATETIME when valueType=DYNAMIC */
    private String dynamicType;

    private String mode;

    private boolean mandatory;

    private int length;

    private String format;

    private FieldValidation validation;

    /** BRD FROM_REQUEST: source DE number */
    private Integer sourceField;

    private java.util.List<ConditionalValueEntry> conditionalValues;
}