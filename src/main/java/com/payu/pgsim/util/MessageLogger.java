package com.payu.pgsim.util;

import com.payu.pgsim.model.MessageLog;
import com.payu.pgsim.model.MessageDirection;
import com.payu.pgsim.model.SimulatorMode;
import com.payu.pgsim.store.TransactionLogStore;

import lombok.RequiredArgsConstructor;

import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MessageLogger {
    private static final Logger log = LoggerFactory.getLogger(MessageLogger.class);

    private final TransactionLogStore logStore;
    public MessageLog logTransaction(ISOMsg request,
                                     ISOMsg response,
                                     long processingTime,
                                     String connectionId) {
        MessageLog incoming = buildIncomingLog(request, response, processingTime, connectionId);
        MessageLog outgoing = buildOutgoingLog(request, response, processingTime, connectionId);
        logStore.add(incoming);
        logStore.add(outgoing);
        printLog(incoming);
        printLog(outgoing);
        return outgoing;
    }

    private MessageLog buildIncomingLog(ISOMsg request,
                                        ISOMsg response,
                                        long processingTime,
                                        String connectionId) {
        MessageLog log = new MessageLog();

        log.setTimestamp(LocalDateTime.now());
        log.setConnectionId(connectionId);
        log.setProcessingTime(processingTime);

        try {
            log.setMti(request.getMTI());
        } catch (Exception e) {
            log.setMti("UNKNOWN");
        }

        try {
            byte[] requestBytes = request.pack();
            log.setDirection(MessageDirection.INCOMING);
            log.setMode(SimulatorMode.SERVER);
            log.setRawMessage(requestBytes);
            log.setHexMessage(HexUtil.toHex(requestBytes));
        } catch (Exception e) {
            MessageLogger.log.warn("Unable to build HEX message for transaction log", e);
        }

        log.setRequestFields(extractFields(request));
        log.setResponseFields(extractFields(response));

        if (response.hasField(39)) {
            try {
                log.setResponseCode(response.getString(39));
            } catch (Exception e) {
                log.setResponseCode("ERR");
            }
        }
        return log;
    }

    private MessageLog buildOutgoingLog(ISOMsg request,
                                        ISOMsg response,
                                        long processingTime,
                                        String connectionId) {
        MessageLog log = new MessageLog();

        log.setTimestamp(LocalDateTime.now());
        log.setConnectionId(connectionId);
        log.setProcessingTime(processingTime);

        try {
            log.setMti(response != null ? response.getMTI() : request.getMTI());
        } catch (Exception e) {
            log.setMti("UNKNOWN");
        }

        try {
            if (response != null) {
                byte[] responseBytes = response.pack();
                log.setDirection(MessageDirection.OUTGOING);
                log.setMode(SimulatorMode.SERVER);
                log.setRawMessage(responseBytes);
                log.setHexMessage(HexUtil.toHex(responseBytes));
            }
        } catch (Exception e) {
            MessageLogger.log.warn("Unable to build outgoing HEX message for transaction log", e);
        }

        log.setRequestFields(extractFields(request));
        log.setResponseFields(extractFields(response));

        if (response != null && response.hasField(39)) {
            try {
                log.setResponseCode(response.getString(39));
            } catch (Exception e) {
                log.setResponseCode("ERR");
            }
        }
        return log;
    }

    private Map<Integer,String> extractFields(ISOMsg msg){

        Map<Integer,String> fields = new HashMap<>();

        for(int i = 1; i <= 128; i++){

            if(msg.hasField(i)){

                try {
                    fields.put(i, msg.getString(i));
                } catch (Exception e) {
                    log.warn("Error reading field {}", i, e);
                }

            }

        }

        return fields;
    }

    private void printLog(MessageLog messageLog){

        log.info("====== ISO8583 Transaction ======");
        log.info("Time: {}", messageLog.getTimestamp());
        log.info("Connection: {}", messageLog.getConnectionId());
        log.info("MTI: {}", messageLog.getMti());
        log.info("Response Code: {}", messageLog.getResponseCode());
        log.info("Processing Time: {} ms", messageLog.getProcessingTime());
        if (messageLog.getHexMessage() != null && !messageLog.getHexMessage().isBlank()) {
            if (MessageDirection.INCOMING.equals(messageLog.getDirection())) {
                log.info("Incoming HEX: {}", messageLog.getHexMessage());
            } else if (MessageDirection.OUTGOING.equals(messageLog.getDirection())) {
                log.info("Outgoing HEX: {}", messageLog.getHexMessage());
            } else {
                log.info("HEX Message: {}", messageLog.getHexMessage());
            }
        }
        log.info("Request Fields: {}", messageLog.getRequestFields());
        log.info("Response Fields: {}", messageLog.getResponseFields());
        log.info("=================================");
    }
}