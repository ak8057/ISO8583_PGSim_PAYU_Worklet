package com.payu.pgsim.model;

import lombok.Data;

import java.util.List;

@Data
public class FieldValidation {

    private Integer minLength;
    private Integer maxLength;
    private String pattern;
    private String format;
    private List<String> allowedValues;
}
