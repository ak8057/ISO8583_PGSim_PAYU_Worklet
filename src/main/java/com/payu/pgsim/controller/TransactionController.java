package com.payu.pgsim.controller;

import com.payu.pgsim.model.MessageLog;
import com.payu.pgsim.store.TransactionLogStore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionLogStore store;

    @GetMapping
    public List<MessageLog> getTransactions(){
        return store.getAll();
    }

}