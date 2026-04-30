package com.payu.pgsim.brd.dto;

import lombok.Data;

@Data
public class BrdMessageTypeConfig {

    private String mti;
    private String description;
    private BrdRequestConfig requestConfig;
    private BrdResponseConfig responseConfig;
}
