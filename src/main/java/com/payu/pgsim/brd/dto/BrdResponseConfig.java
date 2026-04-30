package com.payu.pgsim.brd.dto;

import lombok.Data;

import java.util.List;

@Data
public class BrdResponseConfig {

    private String responseMti;
    private List<Integer> responseBits;
    private List<BrdFieldConfig> fieldConfigs;
    private String defaultResponseCode;
}
