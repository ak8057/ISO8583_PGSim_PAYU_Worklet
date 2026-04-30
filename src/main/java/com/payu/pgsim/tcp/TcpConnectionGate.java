package com.payu.pgsim.tcp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class TcpConnectionGate {

    private final int maxConnections;
    private final AtomicInteger active = new AtomicInteger(0);

    public TcpConnectionGate(@Value("${pgsim.tcp.max-connections:0}") int maxConnections) {
        this.maxConnections = maxConnections;
    }

    /**
     * @return true if connection may proceed (caller must call {@link #release()} on close)
     */
    public boolean tryAcquire() {
        if (maxConnections <= 0) {
            active.incrementAndGet();
            return true;
        }
        for (;;) {
            int c = active.get();
            if (c >= maxConnections) {
                return false;
            }
            if (active.compareAndSet(c, c + 1)) {
                return true;
            }
        }
    }

    public void release() {
        if (maxConnections <= 0) {
            active.decrementAndGet();
            return;
        }
        active.decrementAndGet();
    }

    public int getActiveCount() {
        return Math.max(0, active.get());
    }
}
