package com.pgmock.toss.controller;

import com.pgmock.common.chaos.ChaosProperties;
import com.pgmock.toss.service.TossPaymentService;
import com.pgmock.toss.store.PaymentStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestResetController {

    private final PaymentStore paymentStore;
    private final TossPaymentService paymentService;
    private final ChaosProperties chaosProperties;

    public TestResetController(PaymentStore paymentStore, TossPaymentService paymentService,
                               ChaosProperties chaosProperties) {
        this.paymentStore = paymentStore;
        this.paymentService = paymentService;
        this.chaosProperties = chaosProperties;
    }

    @DeleteMapping("/test/reset")
    public ResponseEntity<Void> reset() {
        paymentStore.clear();
        paymentService.clearIdempotencyCache();
        chaosProperties.reset();
        return ResponseEntity.noContent().build();
    }
}
