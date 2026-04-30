package com.payu.pgsim.service;

import com.payu.pgsim.handler.MessageHandler;
import com.payu.pgsim.model.MessageLog;
import com.payu.pgsim.model.MessageDirection;
import com.payu.pgsim.model.MessageTypeConfig;
import com.payu.pgsim.model.MtiProfile;
import com.payu.pgsim.model.SimulatorMode;
import com.payu.pgsim.model.SimulatorRequest;
import com.payu.pgsim.model.SimulatorResponse;
import com.payu.pgsim.parser.Iso8583Parser;
import com.payu.pgsim.store.MessageLogStore;
import com.payu.pgsim.tcp.TcpClient;
import com.payu.pgsim.tcp.TransportModeManager;
import com.payu.pgsim.util.HexUtil;
import com.payu.pgsim.validator.ProfileValidator;
import lombok.RequiredArgsConstructor;
import org.jpos.iso.ISOMsg;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SimulatorService {

    private final MessageBuilder messageBuilder;
    private final OutgoingMessageBuilder outgoingMessageBuilder;
    private final MessageHandler messageHandler;
    private final Iso8583Parser parser;
    private final ObjectProvider<TcpClient> tcpClientProvider;
    private final ObjectProvider<TransportModeManager> transportModeManager;
    private final MessageLogStore messageLogStore;
    private final RuntimeStats runtimeStats;
    private final org.springframework.beans.factory.ObjectProvider<ProfileClientService> profileClientServiceProvider;
    private final ProfileService profileService;
    private final ProfileValidator profileValidator;

    private ProfileClientService getProfileClientService() {
        return profileClientServiceProvider.getIfAvailable();
    }

    /**
     * Ensures the outbound ISO message matches the same MTI profile rules the server will apply,
     * so the client API returns {@code VALIDATION_ERROR} instead of silently padding mandatory
     * fields or receiving a generic DE39=96 over TCP.
     */
    private void validateClientOutgoingAgainstProfile(ISOMsg isoRequest,
            SimulatorRequest request,
            MessageTypeConfig serverProfile) {
        MtiProfile profile = null;
        if (serverProfile != null) {
            profile = ProfileService.toProfile(serverProfile);
        } else {
            profile = profileService.getProfileByRequestMti(request.getMti());
        }
        if (profile != null) {
            profileValidator.validate(isoRequest, profile);
        }
    }

    public SimulatorResponse simulate(SimulatorRequest request) throws Exception {

        String mode = "SERVER";
        TransportModeManager mm = transportModeManager.getIfAvailable();
        if (mm != null) {
            mode = mm.getCurrentMode();
        }
        ISOMsg isoRequest;
        if ("CLIENT".equalsIgnoreCase(mode)) {
            MessageTypeConfig serverProfile = null;
            ProfileClientService profileClientService = getProfileClientService();
            if (profileClientService != null) {
                serverProfile = profileClientService.fetchProfileFromServer(request.getMti());
            }
            if (serverProfile == null) {
                org.slf4j.LoggerFactory.getLogger(SimulatorService.class)
                    .warn("[CLIENT] Falling back to LOCAL config for MTI {}", request.getMti());
            }
            // Normalize fields: "DE2" -> "2"
            if (request.getFields() != null) {
                Map<String, String> normalized = new HashMap<>();
                for (Map.Entry<String, String> entry : request.getFields().entrySet()) {
                    String key = entry.getKey().toUpperCase().replace("DE", "");
                    normalized.put(key, entry.getValue());
                }
                request.setFields(normalized);
            }

            org.slf4j.LoggerFactory.getLogger(SimulatorService.class).info("[CLIENT] Sending MTI: {} with fields: {}", request.getMti(), request.getFields());
            isoRequest = outgoingMessageBuilder.build(request, serverProfile);
            validateClientOutgoingAgainstProfile(isoRequest, request, serverProfile);

            // Output Request debug logs
            Map<Integer, String> isoFields = extractFields(isoRequest);
            org.slf4j.LoggerFactory.getLogger(SimulatorService.class)
                .info("[CLIENT] Built ISOMsg MTI: {} | Bitmap/Fields present: {}", isoRequest.getMTI(), isoFields.keySet());
        } else {
            isoRequest = messageBuilder.buildMessage(request);
        }

        byte[] requestBytes = isoRequest.pack();
        byte[] rawResponse;
        long t0 = System.currentTimeMillis();
        if ("CLIENT".equalsIgnoreCase(mode)) {
            TcpClient tcpClient = tcpClientProvider.getIfAvailable();
            if (tcpClient == null) {
                throw new IllegalStateException("simulator.mode=CLIENT but TcpClient bean is unavailable");
            }
            runtimeStats.incrementSent();
            messageLogStore.addLog(buildClientOutgoingLog(isoRequest, requestBytes));
            rawResponse = tcpClient.sendAndReceive(requestBytes, correlationKey(isoRequest));
            runtimeStats.incrementReceived();
        } else {
            rawResponse = messageHandler.process(requestBytes);
        }
        if (rawResponse == null || rawResponse.length == 0) {
            throw new RuntimeException("Empty response from simulator");
        }


        ISOMsg response = new ISOMsg();

        // IMPORTANT: set packager before unpack
        response.setPackager(parser.getPackager());

        response.unpack(rawResponse);

        if ("CLIENT".equalsIgnoreCase(mode)) {
            messageLogStore.addLog(buildClientIncomingLog(response, rawResponse, System.currentTimeMillis() - t0));
            
            // Output Response debug logs
            Map<Integer, String> parsedFields = extractFields(response);
            org.slf4j.LoggerFactory.getLogger(SimulatorService.class)
                .info("[CLIENT] Received Response MTI: {} with fields: {}", response.getMTI(), parsedFields);
        }

        SimulatorResponse simulatorResponse = new SimulatorResponse();

        simulatorResponse.setMti(response.getMTI());

        Map<Integer,String> fields = new HashMap<>();

        for(int i = 1; i <= 128; i++){
            if(response.hasField(i)){
                fields.put(i,response.getString(i));
            }
        }

        simulatorResponse.setFields(fields);

        return simulatorResponse;
    }

    private MessageLog buildClientOutgoingLog(ISOMsg request, byte[] raw) throws Exception {
        MessageLog log = new MessageLog();
        log.setLogId(UUID.randomUUID().toString());
        log.setTimestamp(LocalDateTime.now());
        log.setConnectionId("CLIENT-OUTBOUND");
        log.setDirection(MessageDirection.OUTGOING);
        log.setMode(SimulatorMode.CLIENT);
        log.setMti(request.getMTI());
        log.setRawMessage(raw);
        log.setHexMessage(HexUtil.toHex(raw));
        log.setParsedFields(extractFields(request));
        return log;
    }

    private MessageLog buildClientIncomingLog(ISOMsg response, byte[] raw, long ms) throws Exception {
        MessageLog log = new MessageLog();
        log.setLogId(UUID.randomUUID().toString());
        log.setTimestamp(LocalDateTime.now());
        log.setConnectionId("CLIENT-INBOUND");
        log.setDirection(MessageDirection.INCOMING);
        log.setMode(SimulatorMode.CLIENT);
        log.setMti(response.getMTI());
        log.setRawMessage(raw);
        log.setHexMessage(HexUtil.toHex(raw));
        log.setParsedFields(extractFields(response));
        if (response.hasField(39)) {
            log.setResponseCode(response.getString(39));
        }
        log.setProcessingTime(ms);
        return log;
    }

    private Map<Integer, String> extractFields(ISOMsg msg) {
        Map<Integer, String> fields = new HashMap<>();
        if (msg == null) {
            return fields;
        }
        for (int i = 1; i <= 128; i++) {
            if (msg.hasField(i)) {
                fields.put(i, msg.getString(i));
            }
        }
        return fields;
    }

    private String correlationKey(ISOMsg msg) {
        try {
            if (msg != null && msg.hasField(11)) {
                return "11:" + msg.getString(11);
            }
            if (msg != null && msg.hasField(37)) {
                return "37:" + msg.getString(37);
            }
        } catch (Exception ignored) {
            // fallback below
        }
        return "__DEFAULT__";
    }
}