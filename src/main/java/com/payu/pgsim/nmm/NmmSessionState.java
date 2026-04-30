package com.payu.pgsim.nmm;

/**
 * Client-side NMM session lifecycle states.
 *
 * Transitions:
 *  DISCONNECTED  ──connect──►  CONNECTING
 *  CONNECTING    ──logon──►    LOGON_SENT
 *  LOGON_SENT    ──0810──►     ACTIVE
 *  ACTIVE        ──logoff──►   LOGOFF_SENT
 *  LOGOFF_SENT   ──0810──►     DISCONNECTED
 *  (any)         ──fail──►     DISCONNECTED  (then auto-reconnect if enabled)
 */
public enum NmmSessionState {
    DISCONNECTED,
    CONNECTING,
    LOGON_SENT,
    ACTIVE,
    LOGOFF_SENT
}
