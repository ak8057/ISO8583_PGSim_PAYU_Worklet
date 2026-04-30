package com.payu.pgsim.brd.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.payu.pgsim.model.FieldValidation;
import lombok.Data;

import java.util.List;

@Data
public class BrdFieldConfig {

    private int fieldNumber;
    private String fieldName;
    private String dataType;
    private boolean mandatory;

    @JsonProperty("valueType")
    @JsonAlias({ "mode" })
    private String valueType;
    private String value;
    private String template;
    private String dynamicType;
    private Integer sourceField;
    private FieldValidation validation;
    private List<BrdConditionalValue> conditionalValues;
}
