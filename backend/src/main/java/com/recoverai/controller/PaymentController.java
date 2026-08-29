package com.recoverai.controller;

import com.recoverai.entity.Payment;
import com.recoverai.service.PaymentService;
import com.recoverai.RecoveryDecisionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.recoverai.repository.RecoveryDecisionRepository;



import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final RecoveryDecisionRepository recoveryDecisionRepository;

    private final RecoveryDecisionService recoveryDecisionService;public PaymentController(
            PaymentService paymentService,
            RecoveryDecisionService recoveryDecisionService,
            RecoveryDecisionRepository recoveryDecisionRepository) {

        this.paymentService = paymentService;
        this.recoveryDecisionService = recoveryDecisionService;
        this.recoveryDecisionRepository = recoveryDecisionRepository;

    }

    @PostMapping
    public ResponseEntity<Payment> createPayment(@RequestBody Payment payment) {
        return ResponseEntity.ok(paymentService.createPayment(payment));
    }

    @GetMapping
    public ResponseEntity<List<Payment>> getAllPayments() {
        return ResponseEntity.ok(paymentService.getAllPayments());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Payment> getPaymentById(@PathVariable Long id) {
        return paymentService.getPaymentById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Payment>> getPaymentsByStatus(
            @PathVariable String status) {
        return ResponseEntity.ok(
                paymentService.getPaymentsByStatus(status)
        );
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<Payment>> getPaymentsByCustomer(
            @PathVariable String customerId) {
        return ResponseEntity.ok(
                paymentService.getPaymentsByCustomer(customerId)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<Payment> updatePayment(
            @PathVariable Long id,
            @RequestBody Payment payment) {

        try {
            return ResponseEntity.ok(
                    paymentService.updatePayment(id, payment)
            );
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePayment(@PathVariable Long id) {
        paymentService.deletePayment(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/classify")
    public ResponseEntity<?> classifyPayment(@PathVariable Long id) {

        return paymentService.getPaymentById(id)
                .map(payment ->
                        ResponseEntity.ok(
                                recoveryDecisionService.classify(payment)
                        )
                )
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/decision")
    public ResponseEntity<?> getLatestDecision(@PathVariable Long id) {

        return recoveryDecisionRepository
                .findTopByPaymentIdOrderByRecommendedAtDesc(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
