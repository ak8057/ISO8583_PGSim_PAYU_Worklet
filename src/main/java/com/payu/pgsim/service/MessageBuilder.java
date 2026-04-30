package com.payu.pgsim.service;

import com.payu.pgsim.model.SimulatorRequest;
import lombok.RequiredArgsConstructor;
import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageBuilder {

    private static final Logger log = LoggerFactory.getLogger(MessageBuilder.class);

    private final OutgoingMessageBuilder outgoingMessageBuilder;

    public ISOMsg buildMessage(SimulatorRequest request) throws Exception {
        try {
            return outgoingMessageBuilder.build(request);
        } catch (Exception e) {
            log.warn("OutgoingMessageBuilder failed, rethrowing", e);
            throw e;
        }
    }
}