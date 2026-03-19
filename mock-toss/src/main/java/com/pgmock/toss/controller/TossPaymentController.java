package com.pgmock.toss.controller;

import com.pgmock.toss.auth.TossAuthValidator;
import com.pgmock.toss.service.TossPaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/payments")
public class TossPaymentController {

    private final TossAuthValidator authValidator;
    private final TossPaymentService paymentService;

    public TossPaymentController(TossAuthValidator authValidator, TossPaymentService paymentService) {
        this.authValidator = authValidator;
        this.paymentService = paymentService;
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ConfirmRequest request) {

        ResponseEntity<?> authError = authValidator.validate(authorization);
        if (authError != null) return authError;

        return paymentService.confirm(request.paymentKey(), request.orderId(), request.amount(), idempotencyKey);
    }

    @GetMapping("/{paymentKey}")
    public ResponseEntity<?> getPayment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String paymentKey) {

        ResponseEntity<?> authError = authValidator.validate(authorization);
        if (authError != null) return authError;

        return paymentService.getPayment(paymentKey);
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<?> getPaymentByOrderId(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String orderId) {

        ResponseEntity<?> authError = authValidator.validate(authorization);
        if (authError != null) return authError;

        return paymentService.getPaymentByOrderId(orderId);
    }

    @PostMapping("/{paymentKey}/cancel")
    public ResponseEntity<?> cancel(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable String paymentKey,
            @RequestBody CancelRequest request) {

        ResponseEntity<?> authError = authValidator.validate(authorization);
        if (authError != null) return authError;

        return paymentService.cancel(paymentKey, request.cancelReason(), request.cancelAmount(), idempotencyKey);
    }

    public record ConfirmRequest(String paymentKey, String orderId, long amount) {}
    public record CancelRequest(String cancelReason, Long cancelAmount) {}
}
