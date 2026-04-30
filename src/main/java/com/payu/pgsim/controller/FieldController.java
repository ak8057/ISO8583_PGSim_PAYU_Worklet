package com.payu.pgsim.controller;

import com.payu.pgsim.config.ConfigManager;
import com.payu.pgsim.model.FieldConfig;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/field")
@RequiredArgsConstructor
public class FieldController {

    private final ConfigManager configManager;

    @PostMapping("/{mti}")
    public String addField(
            @PathVariable("mti") String mti,
            @RequestBody FieldConfig field){

        // Trust frontend routing to correct endpoint
        // This legacy endpoint defaults to response field for backward compatibility
        configManager.addResponseField(mti, field);

        return "Field added";
    }
}