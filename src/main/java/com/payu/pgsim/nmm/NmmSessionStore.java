package com.payu.pgsim.nmm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SERVER-SIDE: tracks the NMM session state for each connected client.
 *
 * Key   = connectionId (same UUID used by TcpServerHandler / ConnectionStore)
 * Value = most recent NMM state seen on that connection
 *
 * Transitions are driven by NmmServerObserver after it detects 0800 messages.
 */
@Component
public class NmmSessionStore {

    private static final Logger log = LoggerFactory.getLogger(NmmSessionStore.class);

    private final Map<String, NmmSessionState> sessions = new ConcurrentHashMap<>();

    /** Called when the server receives a LOGON (DE70=001) from a client. */
    public void onLogon(String connectionId) {
        sessions.put(connectionId, NmmSessionState.ACTIVE);
        log.info("[SERVER] Session ACTIVE after LOGON  | connectionId={}", connectionId);
    }

    /** Called when the server receives a LOGOFF (DE70=002) from a client. */
    public void onLogoff(String connectionId) {
        sessions.put(connectionId, NmmSessionState.DISCONNECTED);
        log.info("[SERVER] Session CLOSED after LOGOFF | connectionId={}", connectionId);
    }

    /** Called when the server receives an ECHO (DE70=301) from a client. */
    public void onEcho(String connectionId) {
        log.info("[SERVER] ECHO received                | connectionId={}", connectionId);
    }

    /** Called when the underlying TCP connection is dropped. */
    public void onDisconnect(String connectionId) {
        NmmSessionState prev = sessions.remove(connectionId);
        if (prev != null) {
            log.info("[SERVER] NMM session removed (disconnect) | connectionId={} lastState={}",
                    connectionId, prev);
        }
    }

    /** Returns the current NMM session state for a connection, or null if none. */
    public NmmSessionState getState(String connectionId) {
        return sessions.get(connectionId);
    }

    /** Returns true when the connection has completed a successful LOGON. */
    public boolean isActive(String connectionId) {
        return NmmSessionState.ACTIVE == sessions.get(connectionId);
    }
}
