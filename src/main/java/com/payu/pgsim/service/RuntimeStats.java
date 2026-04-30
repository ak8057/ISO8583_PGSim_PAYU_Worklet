package com.payu.pgsim.service;

import com.payu.pgsim.model.MessageStatistics;
import com.payu.pgsim.model.SimulatorStatus;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class RuntimeStats {

    private final AtomicLong totalReceived = new AtomicLong();
    private final AtomicLong totalSent = new AtomicLong();
    private final AtomicLong errorCount = new AtomicLong();
    private final AtomicReference<String> lastError = new AtomicReference<>();

    private final long startTime = System.currentTimeMillis();

    public void incrementReceived() {
        totalReceived.incrementAndGet();
    }

    public void incrementSent() {
        totalSent.incrementAndGet();
    }

    public void incrementError() {
        errorCount.incrementAndGet();
    }

    public void recordError(Throwable t) {
        errorCount.incrementAndGet();
        if (t != null) {
            String msg = t.getMessage();
            lastError.set(msg != null ? msg : t.getClass().getSimpleName());
        }
    }

    public SimulatorStatus getStatus(
            int activeConnections,
            int tcpServerPort,
            int tcpClientPort,
            long configurationCount,
            long configurationVersion) {
        SimulatorStatus status = new SimulatorStatus();

        status.setStatus("RUNNING");
        status.setUptime(System.currentTimeMillis() - startTime);
        status.setActiveConnections(activeConnections);
        status.setTotalMessagesReceived(totalReceived.get());
        status.setTotalMessagesSent(totalSent.get());
        status.setErrorCount(errorCount.get());
        status.setLastError(lastError.get());
        status.setTcpPort(tcpServerPort);
        status.setTcpServerPort(tcpServerPort);
        status.setTcpClientPort(tcpClientPort);
        status.setConfigurationCount(configurationCount);
        status.setConfigurationVersion(configurationVersion);

        return status;
    }

    public MessageStatistics getStatistics(int activeConnections, long configurationCount, long configurationVersion) {
        MessageStatistics s = new MessageStatistics();
        s.setTotalMessagesReceived(totalReceived.get());
        s.setTotalMessagesSent(totalSent.get());
        s.setErrorCount(errorCount.get());
        s.setActiveConnections(activeConnections);
        s.setUptimeMillis(System.currentTimeMillis() - startTime);
        s.setConfigurationCount(configurationCount);
        s.setConfigurationVersion(configurationVersion);
        return s;
    }
}
