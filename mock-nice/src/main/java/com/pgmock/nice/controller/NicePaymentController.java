package com.pgmock.nice.controller;

import com.pgmock.nice.auth.NiceAuthValidator;
import com.pgmock.nice.service.NicePaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/payments")
public class NicePaymentController {

    private final NiceAuthValidator authValidator;
    private final NicePaymentService paymentService;

    public NicePaymentController(NiceAuthValidator authValidator, NicePaymentService paymentService) {
        this.authValidator = authValidator;
        this.paymentService = paymentService;
    }

    @PostMapping("/{tid}")
    public ResponseEntity<?> approve(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String tid,
            @RequestBody ApproveRequest request) {

        ResponseEntity<?> authError = authValidator.validate(authorization);
        if (authError != null) return authError;

        return paymentService.approve(tid, request.amount());
    }

    @GetMapping("/{tid}")
    public ResponseEntity<?> getPaymentByTid(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String tid) {

        ResponseEntity<?> authError = authValidator.validate(authorization);
        if (authError != null) return authError;

        return paymentService.getPaymentByTid(tid);
    }

    @GetMapping("/find/{orderId}")
    public ResponseEntity<?> getPaymentByOrderId(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String orderId) {

        ResponseEntity<?> authError = authValidator.validate(authorization);
        if (authError != null) return authError;

        return paymentService.getPaymentByOrderId(orderId);
    }

    @PostMapping("/{tid}/cancel")
    public ResponseEntity<?> cancel(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String tid,
            @RequestBody CancelRequest request) {

        ResponseEntity<?> authError = authValidator.validate(authorization);
        if (authError != null) return authError;

        return paymentService.cancel(tid, request.reason(), request.orderId(), request.cancelAmt());
    }

    public record ApproveRequest(long amount) {}
    public record CancelRequest(String reason, String orderId, Long cancelAmt) {}
}
