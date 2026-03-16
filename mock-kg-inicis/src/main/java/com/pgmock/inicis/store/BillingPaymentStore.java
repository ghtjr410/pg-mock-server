package com.pgmock.inicis.store;

import com.pgmock.inicis.domain.BillingPayment;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class BillingPaymentStore {

    private final ConcurrentHashMap<String, BillingPayment> store = new ConcurrentHashMap<>();

    public void save(BillingPayment payment) {
        if (payment.getTid() != null) {
            store.put(payment.getTid(), payment);
        }
    }

    public BillingPayment findByTid(String tid) {
        return store.get(tid);
    }
}
