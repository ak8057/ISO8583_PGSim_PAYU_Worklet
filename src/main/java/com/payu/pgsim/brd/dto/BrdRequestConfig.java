package com.payu.pgsim.brd.dto;

import lombok.Data;

import java.util.List;

@Data
public class BrdRequestConfig {

    private List<Integer> mandatoryBits;
    private List<Integer> optionalBits;
    /** When set, overrides automatic secondary-bitmap detection from DE > 64. */
    private Boolean secondaryBitmap;
    private List<BrdFieldConfig> fieldConfigs;
}
