package com.payu.pgsim.service;

import org.springframework.stereotype.Component;

@Component
public class MtiRouter {

    public String getResponseMti(String requestMti){
        if (requestMti == null || requestMti.length() != 4) {
            return null;
        }

        char[] mtiChars = requestMti.toCharArray();

        // For reversal messages, BRD requires advice-response style MTI (*3*).
        if (mtiChars[1] == '4') {
            mtiChars[2] = '3';
            return new String(mtiChars);
        }

        if (mtiChars[2] == '0') {
            mtiChars[2] = '1';
            return new String(mtiChars);
        }

        if (mtiChars[2] == '2') {
            mtiChars[2] = '3';
            return new String(mtiChars);
        }

        return null;
    }
}