package com.payu.pgsim.store;

import com.payu.pgsim.model.MessageLog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Component
public class MessageLogStore {

    private final int maxLogs;
    private final List<MessageLog> logs = new CopyOnWriteArrayList<>();

    public MessageLogStore(@Value("${pgsim.logs.max-entries:10000}") int maxLogs) {
        this.maxLogs = Math.max(100, maxLogs);
    }

    public void addLog(MessageLog log) {
        while (logs.size() >= maxLogs) {
            logs.remove(0);
        }
        logs.add(log);
    }

    public List<MessageLog> getLogs() {
        return new ArrayList<>(logs);
    }

    public Optional<MessageLog> getById(String logId) {
        if (logId == null) {
            return Optional.empty();
        }
        return logs.stream().filter(l -> logId.equals(l.getLogId())).findFirst();
    }

    public List<MessageLog> query(LocalDateTime from, LocalDateTime to, String mti, int limit) {
        int cap = limit <= 0 ? maxLogs : Math.min(limit, maxLogs);
        return logs.stream()
                .filter(l -> from == null || !l.getTimestamp().isBefore(from))
                .filter(l -> to == null || !l.getTimestamp().isAfter(to))
                .filter(l -> mti == null || mti.isBlank() || mti.equals(l.getMti()))
                .sorted(Comparator.comparing(MessageLog::getTimestamp).reversed())
                .limit(cap)
                .collect(Collectors.toList());
    }

    public int getMaxLogs() {
        return maxLogs;
    }
}
