package com.payu.pgsim.brd.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BrdParsedMessage {

    private String mti;
    private Map<String, String> fields;
}
