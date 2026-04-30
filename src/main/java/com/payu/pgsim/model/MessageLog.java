package com.payu.pgsim.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class MessageLog {

    private String logId;

    private MessageDirection direction;

    private SimulatorMode mode;

    private String connectionId;

    private String mti;

    private byte[] rawMessage;

    private String hexMessage;

    private Map<Integer, String> parsedFields;

    private Map<Integer,String> requestFields;

    private Map<Integer,String> responseFields;

    private String responseCode;

    private LocalDateTime timestamp;

    private long processingTime;

    public void setDirection(String direction) {
        if (direction == null) {
            this.direction = null;
            return;
        }
        this.direction = MessageDirection.valueOf(direction.trim().toUpperCase());
    }

    public void setDirection(MessageDirection direction) {
        this.direction = direction;
    }

    public String getDirectionValue() {
        return direction != null ? direction.name() : null;
    }

}