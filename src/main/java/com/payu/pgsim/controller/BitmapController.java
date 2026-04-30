package com.payu.pgsim.controller;

import com.payu.pgsim.config.ConfigManager;
import com.payu.pgsim.model.BitmapConfig;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bitmap")
@RequiredArgsConstructor
public class BitmapController {

    private final ConfigManager configManager;

    @PostMapping("/{mti}")
    public String updateBitmap(
            @PathVariable("mti") String mti,
            @RequestBody BitmapConfig bitmap){

        configManager.updateBitmap(mti, bitmap);

        return "Bitmap updated";
    }
}