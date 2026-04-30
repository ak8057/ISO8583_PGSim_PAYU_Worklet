package com.payu.pgsim.nmm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the Network Management Message (NMM) subsystem.
 *
 * Enable via:
 *   pgsim.nmm.enabled=true
 */
@Component
@ConfigurationProperties(prefix = "pgsim.nmm")
@Data
public class NmmProperties {

    /** Master switch — when false the entire NMM subsystem is dormant. */
    private boolean enabled = false;

    /** How often (ms) to send a periodic ECHO (DE70=301) while session is ACTIVE. */
    private long echoIntervalMs = 60000;

    /** Max attempts to get a valid response for any single NMM message. */
    private int retryCount = 5;

    /** Pause (ms) between consecutive NMM retry attempts. */
    private long retryDelayMs = 10000;

    /** Automatically re-connect and re-LOGON after unrecoverable failure. */
    private boolean autoReconnect = true;

    /** Timeout (ms) to wait for a single NMM response before retrying. */
    private long responseTimeoutMs = 10000;

    /** Upper bound for reconnect backoff delay (ms). */
    private long maxReconnectDelayMs = 120000;

    /** Random jitter percentage applied to reconnect delay (0-100). */
    private int reconnectJitterPercent = 20;
}
