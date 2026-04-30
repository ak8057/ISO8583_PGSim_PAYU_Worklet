package com.payu.pgsim.controller;

import com.payu.pgsim.model.MessageLog;
import com.payu.pgsim.store.MessageLogStore;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final MessageLogStore logStore;

    /**
     * BRD §8.2.3 — query params {@code from}, {@code to}, {@code mti}, {@code limit} (0 = use store max).
     */
    @GetMapping("/messages")
    public List<MessageLog> getLogs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String mti,
            @RequestParam(required = false, defaultValue = "0") int limit) {
        return logStore.query(from, to, mti, limit);
    }

    @GetMapping("/messages/{id}")
    public MessageLog getLogById(@PathVariable("id") String id) {
        return logStore.getById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "log not found"));
    }

    @GetMapping("/messages/export")
    public List<MessageLog> exportLogs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String mti,
            @RequestParam(required = false, defaultValue = "0") int limit) {
        return logStore.query(from, to, mti, limit);
    }
}
