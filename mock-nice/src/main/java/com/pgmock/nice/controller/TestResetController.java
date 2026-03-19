package com.pgmock.nice.controller;

import com.pgmock.common.chaos.ChaosProperties;
import com.pgmock.nice.store.PaymentStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestResetController {

    private final PaymentStore paymentStore;
    private final ChaosProperties chaosProperties;

    public TestResetController(PaymentStore paymentStore, ChaosProperties chaosProperties) {
        this.paymentStore = paymentStore;
        this.chaosProperties = chaosProperties;
    }

    @DeleteMapping("/test/reset")
    public ResponseEntity<Void> reset() {
        paymentStore.clear();
        chaosProperties.reset();
        return ResponseEntity.noContent().build();
    }
}
