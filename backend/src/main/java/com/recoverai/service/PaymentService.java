package com.recoverai.service;

import com.recoverai.entity.Payment;
import com.recoverai.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public Payment createPayment(Payment payment) {
        if (payment.getStatus() == null || payment.getStatus().isBlank()) {
            payment.setStatus("PENDING");
        }

        if (payment.getRetryCount() == null) {
            payment.setRetryCount(0);
        }

        return paymentRepository.save(payment);
    }

    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    public Optional<Payment> getPaymentById(Long id) {
        return paymentRepository.findById(id);
    }

    public List<Payment> getPaymentsByStatus(String status) {
        return paymentRepository.findByStatus(status);
    }

    public List<Payment> getPaymentsByCustomer(String customerId) {
        return paymentRepository.findByCustomerId(customerId);
    }

    public Payment updatePayment(Long id, Payment updatedPayment) {
        Payment existing = paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        existing.setCustomerId(updatedPayment.getCustomerId());
        existing.setAmount(updatedPayment.getAmount());
        existing.setCurrency(updatedPayment.getCurrency());
        existing.setStatus(updatedPayment.getStatus());
        existing.setFailureReason(updatedPayment.getFailureReason());
        existing.setRetryCount(updatedPayment.getRetryCount());

        return paymentRepository.save(existing);
    }

    public void deletePayment(Long id) {
        paymentRepository.deleteById(id);
    }
}