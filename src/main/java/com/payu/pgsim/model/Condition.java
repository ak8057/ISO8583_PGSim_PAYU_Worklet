package com.payu.pgsim.model;

import lombok.Data;

@Data
public class Condition {

    private int field;

    private String operator;

    private String value;
}
