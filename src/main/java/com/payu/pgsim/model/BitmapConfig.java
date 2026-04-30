package com.payu.pgsim.model;

import lombok.Data;

import java.util.List;

@Data
public class BitmapConfig {

    /**
     * Legacy: all request bits that must be present and form the allowed set.
     * When {@link #mandatoryBits} is set (BRD mode), use mandatory/optional semantics instead.
     */
    private List<Integer> requestBits;

    private List<Integer> responseBits;

    private boolean secondaryBitmap;

    /**
     * BRD §10.1: bits that must appear on the request.
     */
    private List<Integer> mandatoryBits;

    /**
     * BRD §10.1: bits that may appear on the request (optional DEs).
     */
    private List<Integer> optionalBits;

}