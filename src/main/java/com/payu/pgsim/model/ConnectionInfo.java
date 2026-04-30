package com.payu.pgsim.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConnectionInfo {

    private String connectionId;
    private String remoteAddress;
    private int remotePort;
    private int localPort;

    private LocalDateTime connectedAt;
    private LocalDateTime lastActivity;

    private int messageCount;

    private String status; // ACTIVE / CLOSED / TIMEOUT
}
