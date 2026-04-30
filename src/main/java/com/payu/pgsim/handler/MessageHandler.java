package com.payu.pgsim.handler;

import com.payu.pgsim.analyzer.BitMapAnalyzer;
import com.payu.pgsim.config.ConfigManager;
import com.payu.pgsim.generator.ResponseGenerator;
import com.payu.pgsim.model.MessageLog;
import com.payu.pgsim.model.MessageDirection;
import com.payu.pgsim.model.MessageTypeConfig;
import com.payu.pgsim.model.MtiProfile;
import com.payu.pgsim.model.SimulatorMode;
import com.payu.pgsim.model.Transaction;
import com.payu.pgsim.parser.Iso8583Parser;
import com.payu.pgsim.service.MtiRouter;
import com.payu.pgsim.service.ProfileService;
import com.payu.pgsim.service.RuntimeStats;
import com.payu.pgsim.store.MessageLogStore;
import com.payu.pgsim.store.TransactionStore;
import com.payu.pgsim.util.HexUtil;
import com.payu.pgsim.util.MessageLogger;
import com.payu.pgsim.validator.BitmapValidator;
import com.payu.pgsim.validator.FieldValidator;
import com.payu.pgsim.validator.IsoValidationException;
import com.payu.pgsim.validator.ProfileValidator;

import lombok.RequiredArgsConstructor;
import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

@Component
@RequiredArgsConstructor
public class MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(MessageHandler.class);
    private static final String RC_SYSTEM_MALFUNCTION = "96";

    private final Iso8583Parser parser;
    private final FieldValidator fieldValidator;
    private final ConfigManager configManager;
    private final ResponseGenerator responseGenerator;
    private final MessageLogger messageLogger;
    private final TransactionStore transactionStore;
    private final BitmapValidator bitmapValidator;
    private final MtiRouter mtiRouter;

    private final MessageLogStore messageLogStore;
    private final RuntimeStats runtimeStats;
    private final BitMapAnalyzer bitMapAnalyzer;

    // Profile-based validation (new feature – additive, does not break existing behaviour)
    private final ProfileService profileService;
    private final ProfileValidator profileValidator;

    public byte[] process(byte[] rawMessage) {
        ProcessingResult result = processInternal(rawMessage, "REST-CLIENT", true);
        if (result.closeChannel()) {
            throw new ScenarioTimeoutException("Scenario requested TIMEOUT");
        }
        return result.response();
    }

    public ProcessingResult processTcp(byte[] rawMessage, String connectionId) {
        String cid = (connectionId == null || connectionId.isBlank()) ? "TCP-CLIENT" : connectionId;
        return processInternal(rawMessage, cid, false);
    }

    private ProcessingResult processInternal(byte[] rawMessage, String connectionId, boolean throwOnError) {
        long startTime = System.currentTimeMillis();
        ISOMsg request = null;
        MessageTypeConfig config = null;

        try {
            request = parser.parse(rawMessage);

            runtimeStats.incrementReceived();

            log.info("Bitmap analysis (request): present DEs {}", bitMapAnalyzer.getPresentFields(request));

            messageLogStore.addLog(buildIncomingLog(connectionId, request, rawMessage));

            String mti = request.getMTI();

            if ("0110".equals(mti) || "0210".equals(mti)) {
                log.info("Received response MTI {} - logging only, no response will be generated", mti);
                return new ProcessingResult(null, false);
            }

            log.info("Processing MTI: {}", mti);

            config = configManager.getConfig(mti);
            if (config == null) {
                throw new IsoValidationException("Unsupported MTI: " + mti);
            }

            // ── Profile-based validation (preferred path) ─────────────────────
            // If a profile exists for this MTI, use it for validation.
            // Otherwise fall back to the legacy BitmapValidator + FieldValidator.
            MtiProfile profile = profileService.getProfileByRequestMti(mti);
            if (profile != null) {
                log.info("Using profile '{}' for MTI: {}", profile.getProfileId(), mti);
                profileValidator.validate(request, profile);
            } else {
                log.info("No profile found for MTI: {} – using legacy validators", mti);
                bitmapValidator.validate(request, config.getBitmap());
                fieldValidator.validate(request, config);
            }

            updateTransactionState(request, mti);

            ISOMsg response = responseGenerator.generateResponse(request, config);
            log.info("Response MTI: {}", response.getMTI());

            long processingTime = System.currentTimeMillis() - startTime;

            messageLogger.logTransaction(request, response, processingTime, connectionId);

            runtimeStats.incrementSent();

            byte[] responseBytes = response.pack();

            messageLogStore.addLog(buildOutgoingLog(connectionId, response, responseBytes, processingTime));

            return ProcessingResult.respond(responseBytes);

        } catch (ScenarioTimeoutException timeout) {

            runtimeStats.recordError(timeout);

            if (throwOnError) {
                throw timeout;
            }
            return ProcessingResult.closeWithoutResponse();

        } catch (IsoValidationException e) {

            runtimeStats.recordError(e);

            if (throwOnError) {
                throw e;
            }
            byte[] errorBytes = buildSystemMalfunctionResponseBytes(rawMessage, request, config, e);
            return ProcessingResult.respond(errorBytes);

        } catch (Exception e) {

            runtimeStats.recordError(e);

            if (throwOnError) {
                throw new RuntimeException("ISO processing failed: " + e.getMessage(), e);
            }

            byte[] errorBytes = buildSystemMalfunctionResponseBytes(rawMessage, request, config, e);
            return ProcessingResult.respond(errorBytes);
        }
    }

    private MessageLog buildIncomingLog(String connectionId, ISOMsg request, byte[] rawMessage) {
        MessageLog incomingLog = new MessageLog();
        incomingLog.setLogId(UUID.randomUUID().toString());
        incomingLog.setTimestamp(LocalDateTime.now());
        incomingLog.setConnectionId(connectionId);
        incomingLog.setDirection(MessageDirection.INCOMING);
        incomingLog.setMode(SimulatorMode.SERVER);
        incomingLog.setMti(safeGetMti(request));
        incomingLog.setRawMessage(rawMessage);
        incomingLog.setHexMessage(HexUtil.toHex(rawMessage));

        Map<Integer, String> requestFields = extractFields(request);
        incomingLog.setParsedFields(requestFields);

        return incomingLog;
    }

    private MessageLog buildOutgoingLog(String connectionId, ISOMsg response, byte[] responseBytes, long processingTime) {
        MessageLog outgoingLog = new MessageLog();
        outgoingLog.setLogId(UUID.randomUUID().toString());
        outgoingLog.setTimestamp(LocalDateTime.now());
        outgoingLog.setConnectionId(connectionId);
        outgoingLog.setDirection(MessageDirection.OUTGOING);
        outgoingLog.setMode(SimulatorMode.SERVER);
        outgoingLog.setMti(safeGetMti(response));
        outgoingLog.setRawMessage(responseBytes);
        outgoingLog.setHexMessage(HexUtil.toHex(responseBytes));

        Map<Integer, String> responseFields = extractFields(response);
        outgoingLog.setParsedFields(responseFields);

        if (response != null && response.hasField(39)) {
            outgoingLog.setResponseCode(response.getString(39));
        }

        outgoingLog.setProcessingTime(processingTime);
        return outgoingLog;
    }

    private String safeGetMti(ISOMsg msg) {
        try {
            return msg != null ? msg.getMTI() : null;
        } catch (org.jpos.iso.ISOException e) {
            return null;
        }
    }

    private Map<Integer, String> extractFields(ISOMsg msg) {
        Map<Integer, String> fields = new HashMap<>();
        if (msg == null)
            return fields;

        for (int i = 2; i <= 128; i++) {
            if (msg.hasField(i)) {
                fields.put(i, msg.getString(i));
            }
        }
        return fields;
    }

    private byte[] buildSystemMalfunctionResponseBytes(
            byte[] rawMessage,
            ISOMsg request,
            MessageTypeConfig config,
            Exception cause) {

        try {
            ISOMsg response = new ISOMsg();

            if (request == null) {
                log.warn("Parse failed, building fallback ISO response", cause);

                String requestMti = extractMtiFromRawMessage(rawMessage);
                String responseMti = deriveResponseMti(requestMti);

                response.setPackager(parser.getPackager());
                response.setMTI(responseMti);
                response.set(39, "30");

                return response.pack();
            }

            response.setPackager(request.getPackager());

            String responseMti = (config != null && config.getResponseMti() != null)
                    ? config.getResponseMti()
                    : mtiRouter.getResponseMti(request.getMTI());

            response.setMTI(responseMti);

            if (request.hasField(11))
                response.set(11, request.getString(11));
            if (request.hasField(37))
                response.set(37, request.getString(37));

            response.set(39, RC_SYSTEM_MALFUNCTION);

            return response.pack();

        } catch (Exception e) {
            log.error("Critical error building fallback response", e);
            return new byte[0];
        }
    }

    private String extractMtiFromRawMessage(byte[] rawMessage) {
        if (rawMessage == null || rawMessage.length < 4)
            return "0800";
        try {
            String mti = new String(rawMessage, 0, 4);
            return mti.matches("\\d{4}") ? mti : "0800";
        } catch (Exception e) {
            return "0800";
        }
    }

    private String deriveResponseMti(String requestMti) {
        try {
            String res = mtiRouter.getResponseMti(requestMti);
            return res != null ? res : "0810";
        } catch (Exception e) {
            return "0810";
        }
    }

    private void updateTransactionState(ISOMsg request, String mti) {
        if (isReversalMti(mti)) {
            markTransactionReversed(request);
            return;
        }
        if (isTransactionRequest(mti)) {
            storeTransaction(request, mti);
        }
    }

    private void storeTransaction(ISOMsg request, String mti) {
        if (!request.hasField(11))
            return;

        Transaction tx = new Transaction();
        tx.setStan(request.getString(11));
        if (request.hasField(2))
            tx.setPan(request.getString(2));
        if (request.hasField(4))
            tx.setAmount(request.getString(4));
        tx.setMti(mti);
        tx.setReversed(false);

        transactionStore.save(tx);
    }

    private void markTransactionReversed(ISOMsg request) {
        if (!request.hasField(11))
            return;

        Transaction original = transactionStore.find(request.getString(11));
        if (original != null) {
            original.setReversed(true);
        }
    }

    private boolean isReversalMti(String mti) {
        return mti != null && mti.length() == 4 && mti.charAt(1) == '4';
    }

    private boolean isTransactionRequest(String mti) {
        if (mti == null || mti.length() != 4)
            return false;
        char messageClass = mti.charAt(1);
        char function = mti.charAt(2);
        return (messageClass == '1' || messageClass == '2') && (function == '0' || function == '2');
    }
}
