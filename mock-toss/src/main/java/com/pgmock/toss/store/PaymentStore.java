package com.pgmock.toss.store;

import com.pgmock.toss.domain.Payment;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class PaymentStore {

    private final ConcurrentHashMap<String, Payment> store = new ConcurrentHashMap<>();

    public void save(Payment payment) {
        store.put(payment.getPaymentKey(), payment);
    }

    public Payment findByPaymentKey(String paymentKey) {
        return store.get(paymentKey);
    }

    public Payment findByOrderId(String orderId) {
        return store.values().stream()
                .filter(p -> p.getOrderId().equals(orderId))
                .findFirst()
                .orElse(null);
    }
}
