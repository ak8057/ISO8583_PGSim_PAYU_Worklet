package com.payu.pgsim.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ImportResult {

    private boolean success;
    private int importedCount;
    private List<String> errors = new ArrayList<>();
}
