package com.payu.pgsim.analyzer;

import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class BitMapAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(BitMapAnalyzer.class);

    public List<Integer> getPresentFields(ISOMsg msg){

        List<Integer> fields = new ArrayList<>();

        for(int i = 1; i <= 128; i++){

            if(msg.hasField(i)){

                fields.add(i);

            }

        }

        return fields;

    }

    public void printBitmap(ISOMsg msg){

        log.info("Bitmap fields present:");

        for(int i = 1; i <= 128; i++){

            if(msg.hasField(i)){

                log.info("Field {} = {}", i, msg.getString(i));

            }

        }

    }
    public void validateMandatoryFields(ISOMsg msg, int[] mandatory){

    for(int field : mandatory){

        if(!msg.hasField(field)){

            throw new RuntimeException(
                    "Missing mandatory field: " + field);

        }

    }

}

}