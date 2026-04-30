package com.payu.pgsim.model;

import lombok.Data;

import java.util.List;

@Data
public class ResponseRule {

    private String ruleId;

    private int field;

    private String operator;

    private String value;

    private List<Condition> conditions;

    private String logic;

    private String responseCode;

}