package com.payu.pgsim.brd;

import com.payu.pgsim.brd.dto.BrdMessageLog;
import com.payu.pgsim.brd.dto.BrdParsedMessage;
import com.payu.pgsim.model.ConnectionInfo;
import com.payu.pgsim.model.MessageLog;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class BrdLogMapper {

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public BrdMessageLog toBrd(MessageLog log) {
        if (log == null) {
            return null;
        }
        BrdMessageLog b = new BrdMessageLog();
        b.setLogId(log.getLogId());
        b.setConnectionId(log.getConnectionId());
        b.setDirection(log.getDirection() != null ? log.getDirection().name() : null);
        b.setMti(log.getMti());
        b.setResponseCode(log.getResponseCode());
        b.setProcessingTime(log.getProcessingTime());
        if (log.getTimestamp() != null) {
            b.setTimestamp(log.getTimestamp().atOffset(ZoneOffset.UTC).format(ISO_FMT));
        }
        if (log.getRawMessage() != null) {
            b.setRawMessage(new String(log.getRawMessage(), StandardCharsets.ISO_8859_1));
        }
        if (log.getParsedFields() != null) {
            Map<String, String> asString = log.getParsedFields().entrySet().stream()
                    .collect(Collectors.toMap(e -> String.valueOf(e.getKey()), Map.Entry::getValue, (a, c) -> a, HashMap::new));
            b.setParsedMessage(new BrdParsedMessage(log.getMti(), asString));
        }
        return b;
    }

    public com.payu.pgsim.brd.dto.BrdConnectionInfo toBrd(ConnectionInfo c) {
        if (c == null) {
            return null;
        }
        com.payu.pgsim.brd.dto.BrdConnectionInfo b = new com.payu.pgsim.brd.dto.BrdConnectionInfo();
        b.setConnectionId(c.getConnectionId());
        b.setRemoteAddress(c.getRemoteAddress());
        b.setRemotePort(c.getRemotePort());
        b.setLocalPort(c.getLocalPort());
        b.setMessageCount(c.getMessageCount());
        b.setStatus(c.getStatus());
        if (c.getConnectedAt() != null) {
            b.setConnectedAt(c.getConnectedAt().atOffset(ZoneOffset.UTC).format(ISO_FMT));
        }
        if (c.getLastActivity() != null) {
            b.setLastActivity(c.getLastActivity().atOffset(ZoneOffset.UTC).format(ISO_FMT));
        }
        return b;
    }
}
