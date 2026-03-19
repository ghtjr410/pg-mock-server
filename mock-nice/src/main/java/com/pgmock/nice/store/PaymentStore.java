package com.pgmock.nice.store;

import com.pgmock.nice.domain.Payment;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class PaymentStore {

    private final ConcurrentHashMap<String, Payment> store = new ConcurrentHashMap<>();

    public void save(Payment payment) {
        store.put(payment.getTid(), payment);
    }

    public Payment findByTid(String tid) {
        return store.get(tid);
    }

    public void clear() {
        store.clear();
    }

    public Payment findByOrderId(String orderId) {
        return store.values().stream()
                .filter(p -> p.getOrderId().equals(orderId))
                .findFirst()
                .orElse(null);
    }
}
