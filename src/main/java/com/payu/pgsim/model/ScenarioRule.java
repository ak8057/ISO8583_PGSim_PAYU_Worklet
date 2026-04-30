package com.payu.pgsim.model;

import lombok.Data;

@Data
public class ScenarioRule {

    private String type;

    private int delay;

    private String responseCode;

}