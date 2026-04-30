package com.payu.pgsim.brd.dto;

import lombok.Data;

@Data
public class BrdSimulatorStatus {

    private String status;
    private long uptime;
    private int tcpPort;
    private int activeConnections;
    private long totalMessagesReceived;
    private long totalMessagesSent;
    private long errorCount;
    private String lastError;
    private long configurationCount;
    private long configurationVersion;

    private String isoPrimaryPackager;
    private String isoSecondaryPackager;
    private boolean tcpTlsActive;
    private boolean tcpTlsRequested;
    private int httpPort;
    private String isoEncodingNote;
    private String simulatorMode;
}
