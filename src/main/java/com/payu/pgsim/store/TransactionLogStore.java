package com.payu.pgsim.store;

import com.payu.pgsim.model.MessageLog;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TransactionLogStore {

    private static final int MAX_LOG_ENTRIES = 1000;
    private final List<MessageLog> logs = new ArrayList<>();

    public synchronized void add(MessageLog log) {
        logs.add(log);
        while (logs.size() > MAX_LOG_ENTRIES) {
            logs.remove(0);
        }
    }

    public synchronized List<MessageLog> getAll() {
        return logs;
    }

}