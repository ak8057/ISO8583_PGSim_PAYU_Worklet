package com.payu.pgsim.model;

import lombok.Data;

@Data
public class SimulatorStatus {

    private String status;

    private long uptime;

    private int activeConnections;

    private long totalMessagesReceived;
    private long totalMessagesSent;

    private long errorCount;

    private String lastError;

    /** Active ISO TCP server bind port. */
    private int tcpPort;

    /** ISO TCP server bind port (explicit field for dashboard/API consumers). */
    private int tcpServerPort;

    /** ISO TCP client target remote port (used in CLIENT mode). */
    private int tcpClientPort;

    private long configurationCount;

    private long configurationVersion;

    /** Classpath resource for primary ISO packager (ASCII pipeline). */
    private String isoPrimaryPackager;

    /** Optional secondary packager used when primary unpack fails (e.g. binary). */
    private String isoSecondaryPackager;

    /** True when TLS is negotiated on the ISO TCP listener. */
    private boolean tcpTlsActive;

    /** True when TLS was requested via configuration (may be inactive if keystore missing). */
    private boolean tcpTlsRequested;

    /** Spring HTTP port (dashboard / REST). */
    private int httpPort;

    /** Short note for operators (encoding / FR-2). */
    private String isoEncodingNote;

    /** Active runtime transport mode. */
    private String simulatorMode;

    /** Deployment role shown in frontend (SERVER or CLIENT). */
    private String instanceRole;

    /** Whether runtime mode toggle is enabled for this deployment. */
    private boolean modeSwitchEnabled;
}
