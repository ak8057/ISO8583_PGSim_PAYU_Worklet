package com.payu.pgsim.store;

import com.payu.pgsim.model.Transaction;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TransactionStore {

    private static final int MAX_TRANSACTIONS = 5000;
    private final Map<String, Transaction> transactions = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> insertionOrder = new ConcurrentLinkedDeque<>();

    public void save(Transaction tx) {

        if (tx.getStan() == null) {
            return;
        }

        String stan = tx.getStan();
        boolean isNew = transactions.put(stan, tx) == null;
        if (isNew) {
            insertionOrder.addLast(stan);
        }

        evictIfNeeded();
    }

    public Transaction find(String stan) {

        return transactions.get(stan);

    }

    private void evictIfNeeded() {
        while (transactions.size() > MAX_TRANSACTIONS) {
            String oldestStan = insertionOrder.pollFirst();
            if (oldestStan == null) {
                return;
            }
            transactions.remove(oldestStan);
        }
    }

}