package com.payu.pgsim.service;

import com.payu.pgsim.model.MessageTypeConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class ProfileClientService {

    private final RestTemplate restTemplate;
    private final String serverHost;
    private final int serverPort;

    public ProfileClientService(
            @Value("${simulator.remote.server.host:127.0.0.1}") String serverHost,
            @Value("${simulator.remote.server.port:8081}") int serverPort) {
        this.restTemplate = new RestTemplate();
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }

    public MessageTypeConfig fetchProfileFromServer(String mti) {
        String url = String.format("http://%s:%d/api/config/profile/%s", serverHost, serverPort, mti);
        try {
            MessageTypeConfig config = restTemplate.getForObject(url, MessageTypeConfig.class);
            if (config != null) {
                log.info("[CLIENT] Using SERVER profile for MTI {}", mti);
                return config;
            } else {
                log.warn("[CLIENT] Server returned empty profile for MTI {}", mti);
                return null;
            }
        } catch (Exception e) {
            log.warn("[CLIENT] Error fetching SERVER profile for MTI {}: {}", mti, e.getMessage());
            return null;
        }
    }
}
