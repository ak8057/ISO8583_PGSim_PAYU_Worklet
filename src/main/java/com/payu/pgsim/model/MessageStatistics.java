package com.payu.pgsim.model;

import lombok.Data;

@Data
public class MessageStatistics {

    private long totalMessagesReceived;
    private long totalMessagesSent;
    private long errorCount;
    private int activeConnections;
    private long uptimeMillis;
    private long configurationCount;
    private long configurationVersion;
}
