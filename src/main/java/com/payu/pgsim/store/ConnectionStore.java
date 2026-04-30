package com.payu.pgsim.store;

import com.payu.pgsim.model.ConnectionInfo;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConnectionStore {

    private final Map<String, ConnectionInfo> connections = new ConcurrentHashMap<>();

    public void add(ConnectionInfo conn) {
        connections.put(conn.getConnectionId(), conn);
    }

    public void remove(String connectionId) {
        connections.remove(connectionId);
    }

    public Collection<ConnectionInfo> getAll() {
        return connections.values();
    }

    public ConnectionInfo get(String id) {
        return connections.get(id);
    }
}
