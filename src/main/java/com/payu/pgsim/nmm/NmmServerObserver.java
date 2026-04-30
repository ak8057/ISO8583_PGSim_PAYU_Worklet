package com.payu.pgsim.nmm;

import com.payu.pgsim.parser.Iso8583Parser;
import lombok.RequiredArgsConstructor;
import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * SERVER-SIDE: inspects every incoming ISO message after it has been framed by
 * TcpServerHandler and updates NmmSessionStore accordingly.
 *
 * This component is purely additive — it only reads data and updates the
 * session store; it does NOT influence the response generation flow.
 *
 * TcpServerHandler calls {@link #observe(String, byte[])} once per message.
 */
@Component
@RequiredArgsConstructor
public class NmmServerObserver {

    private static final Logger log = LoggerFactory.getLogger(NmmServerObserver.class);

    private final Iso8583Parser parser;
    private final NmmSessionStore sessionStore;

    /**
     * Inspect a raw ISO message arriving on the given connection.
     * Any parse error is swallowed — this must never interrupt the main processing path.
     *
     * @param connectionId  unique connection identifier from TcpServerHandler
     * @param rawMessage    packed ISO bytes (after length-field stripping)
     */
    public void observe(String connectionId, byte[] rawMessage) {
        if (connectionId == null || rawMessage == null || rawMessage.length == 0) {
            return;
        }
        try {
            ISOMsg msg = new ISOMsg();
            msg.setPackager(parser.getPackager());
            msg.unpack(rawMessage);

            String mti = msg.getMTI();
            if (!"0800".equals(mti)) {
                return;  // only care about NMM request MTI
            }

            String de70 = msg.hasField(70) ? msg.getString(70) : null;
            if (de70 == null) {
                return;
            }

            switch (de70) {
                case NmmMessageBuilder.DE70_LOGON  -> {
                    log.info("[SERVER] Received LOGON  | connectionId={}", connectionId);
                    sessionStore.onLogon(connectionId);
                }
                case NmmMessageBuilder.DE70_LOGOFF -> {
                    log.info("[SERVER] Received LOGOFF | connectionId={}", connectionId);
                    sessionStore.onLogoff(connectionId);
                }
                case NmmMessageBuilder.DE70_ECHO   -> {
                    log.info("[SERVER] Received ECHO   | connectionId={}", connectionId);
                    sessionStore.onEcho(connectionId);
                }
                default -> log.debug("[SERVER] Unknown NMM DE70={} | connectionId={}", de70, connectionId);
            }

        } catch (Exception e) {
            // Must not interrupt the main ISO processing path
            log.debug("[SERVER] NmmServerObserver parse error (ignored): {}", e.getMessage());
        }
    }

    /** Called by TcpServerHandler when a TCP connection drops. */
    public void onDisconnect(String connectionId) {
        sessionStore.onDisconnect(connectionId);
    }
}
