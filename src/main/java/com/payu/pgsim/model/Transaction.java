package com.payu.pgsim.model;

import lombok.Data;

@Data
public class Transaction {

    private String stan;

    private String rrn;

    private String pan;

    private String amount;

    private String mti;

    private boolean reversed;

}