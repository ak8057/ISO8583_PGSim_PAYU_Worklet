package com.payu.pgsim.controller;

import com.payu.pgsim.model.SimulatorRequest;
import com.payu.pgsim.model.SimulatorResponse;
import com.payu.pgsim.service.SimulatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simulator")
@RequiredArgsConstructor
public class SimulatorController {

    private final SimulatorService simulatorService;

    @PostMapping("/send")
    public SimulatorResponse simulate(@RequestBody SimulatorRequest request)
            throws Exception {

        return simulatorService.simulate(request);

    }
}