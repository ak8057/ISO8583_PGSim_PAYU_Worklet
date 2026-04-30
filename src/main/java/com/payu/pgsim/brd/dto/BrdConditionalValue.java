package com.payu.pgsim.brd.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class BrdConditionalValue {

    @JsonProperty("condition")
    @JsonAlias({ "when", "expression" })
    private String condition;

    private String value;
}
