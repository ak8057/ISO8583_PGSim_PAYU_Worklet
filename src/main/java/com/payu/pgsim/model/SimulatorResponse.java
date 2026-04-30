package com.payu.pgsim.model;

import lombok.Data;
import java.util.Map;

@Data
public class SimulatorResponse {

    private String mti;

    private Map<Integer,String> fields;

}