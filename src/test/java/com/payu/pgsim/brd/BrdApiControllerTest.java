package com.payu.pgsim.brd;

import com.payu.pgsim.config.ConfigManager;
import com.payu.pgsim.model.MessageTypeConfig;
import com.payu.pgsim.model.ValidationResult;
import com.payu.pgsim.service.RuntimeStats;
import com.payu.pgsim.store.ConnectionStore;
import com.payu.pgsim.store.MessageLogStore;
import com.payu.pgsim.tcp.TlsSupport;
import com.payu.pgsim.validator.ConfigValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BrdApiController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({BrdConfigMapper.class, BrdLogMapper.class})
class BrdApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConfigManager configManager;

    @MockitoBean
    private ConfigValidator configValidator;

    @MockitoBean
    private RuntimeStats runtimeStats;

    @MockitoBean
    private ConnectionStore connectionStore;

    @MockitoBean
    private MessageLogStore messageLogStore;

    @MockitoBean
    private TlsSupport tlsSupport;

    @Test
    void listMessageTypes_empty() throws Exception {
        when(configManager.getAllConfigs()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/brd/v1/config/message-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void validate_returnsResult() throws Exception {
        when(configValidator.validateDetailed(any(MessageTypeConfig.class)))
                .thenReturn(ValidationResult.ok());

        String body = """
                {
                  "mti": "0100",
                  "requestConfig": {
                    "mandatoryBits": [2],
                    "optionalBits": [],
                    "fieldConfigs": []
                  },
                  "responseConfig": {
                    "responseMti": "0110",
                    "responseBits": [39],
                    "fieldConfigs": []
                  }
                }
                """;

        mockMvc.perform(post("/api/brd/v1/config/validate")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void runtimeStatus_mapsFields() throws Exception {
        when(connectionStore.getAll()).thenReturn(Collections.emptyList());
        when(configManager.getAllConfigs()).thenReturn(Collections.emptyList());
        when(configManager.getConfigurationVersion()).thenReturn(5L);
        when(runtimeStats.getStatus(0, 19443, 18080, 0L, 5L))
                .thenAnswer(inv -> {
                    com.payu.pgsim.model.SimulatorStatus s = new com.payu.pgsim.model.SimulatorStatus();
                    s.setStatus("RUNNING");
                    s.setUptime(100L);
                    s.setTcpPort(19443);
                    s.setActiveConnections(0);
                    s.setTotalMessagesReceived(1L);
                    s.setTotalMessagesSent(2L);
                    s.setErrorCount(0L);
                    s.setLastError(null);
                    s.setConfigurationCount(0L);
                    s.setConfigurationVersion(5L);
                    return s;
                });

        mockMvc.perform(get("/api/brd/v1/runtime/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.tcpPort").value(19443))
                .andExpect(jsonPath("$.configurationVersion").value(5))
                .andExpect(jsonPath("$.isoPrimaryPackager").value("iso87ascii.xml"))
                .andExpect(jsonPath("$.tcpTlsActive").value(false));
    }
}
