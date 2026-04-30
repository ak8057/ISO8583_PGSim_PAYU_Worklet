package com.payu.pgsim.brd.dto;

import lombok.Data;

@Data
public class BrdMessageLog {

    private String logId;
    private String timestamp;
    private String connectionId;
    private String direction;
    private String mti;
    private String rawMessage;
    private BrdParsedMessage parsedMessage;
    private String responseCode;
    private long processingTime;
}
