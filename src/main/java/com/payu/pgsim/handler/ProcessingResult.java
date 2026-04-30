package com.payu.pgsim.handler;

public record ProcessingResult(byte[] response, boolean closeChannel) {
    public static ProcessingResult respond(byte[] response) {
        return new ProcessingResult(response, false);
    }

    public static ProcessingResult closeWithoutResponse() {
        return new ProcessingResult(null, true);
    }
}

