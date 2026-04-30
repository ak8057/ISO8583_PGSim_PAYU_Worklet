package com.payu.pgsim.controller;

import com.payu.pgsim.config.ConfigManager;

import com.payu.pgsim.model.ResponseRule;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class RuleController {

    private final ConfigManager configManager;

    @PostMapping("/{mti}/rule")
    public String addRule(
            @PathVariable("mti") String mti,
            @RequestBody ResponseRule rule){

        configManager.addRule(mti,rule);

        return "Rule added";
    }
}