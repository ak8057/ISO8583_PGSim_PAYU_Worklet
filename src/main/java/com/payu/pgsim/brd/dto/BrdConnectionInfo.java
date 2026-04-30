package com.payu.pgsim.brd.dto;

import lombok.Data;

@Data
public class BrdConnectionInfo {

    private String connectionId;
    private String remoteAddress;
    private int remotePort;
    private int localPort;
    private String connectedAt;
    private String lastActivity;
    private int messageCount;
    private String status;
}
