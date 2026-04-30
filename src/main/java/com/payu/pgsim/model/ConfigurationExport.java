package com.payu.pgsim.model;

import lombok.Data;

import java.util.List;

@Data
public class ConfigurationExport {

    private String version;
    private List<MessageTypeConfig> messageTypes;
}
