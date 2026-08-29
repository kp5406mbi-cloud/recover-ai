package com.recoverai.controller;

import com.recoverai.entity.RecoveryAttempt;
import com.recoverai.service.RecoveryAttemptService;
import com.recoverai.service.RecoveryExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recovery-attempts")
public class RecoveryAttemptController {

    private final RecoveryAttemptService recoveryAttemptService;
    private final RecoveryExecutionService recoveryExecutionService;

    public RecoveryAttemptController(
            RecoveryAttemptService recoveryAttemptService,
            RecoveryExecutionService recoveryExecutionService) {

        this.recoveryAttemptService =
                recoveryAttemptService;

        this.recoveryExecutionService =
                recoveryExecutionService;
    }

    @PostMapping
    public ResponseEntity<RecoveryAttempt> createAttempt(
            @RequestBody RecoveryAttempt attempt) {

        return ResponseEntity.ok(
                recoveryAttemptService.createAttempt(attempt)
        );
    }

    @GetMapping
    public ResponseEntity<List<RecoveryAttempt>> getAllAttempts() {

        return ResponseEntity.ok(
                recoveryAttemptService.getAllAttempts()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecoveryAttempt> getAttemptById(
            @PathVariable Long id) {

        return recoveryAttemptService
                .getAttemptById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/payment/{paymentId}")
    public ResponseEntity<List<RecoveryAttempt>>
    getAttemptsByPayment(
            @PathVariable Long paymentId) {

        return ResponseEntity.ok(
                recoveryAttemptService
                        .getAttemptsByPayment(paymentId)
        );
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<RecoveryAttempt>>
    getAttemptsByStatus(
            @PathVariable String status) {

        return ResponseEntity.ok(
                recoveryAttemptService
                        .getAttemptsByStatus(status)
        );
    }

    /*
     * =========================================================
     * EXECUTE RECOVERY NOW
     * =========================================================
     *
     * POST /api/recovery-attempts/{id}/execute
     *
     * Executes an existing scheduled recovery attempt
     * immediately.
     */
    @PostMapping("/{id}/execute")
    public ResponseEntity<?> executeRecovery(
            @PathVariable Long id) {

        try {

            RecoveryAttempt attempt =
                    recoveryExecutionService.executeNow(id);

            return ResponseEntity.ok(attempt);

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .notFound()
                    .build();

        } catch (Exception e) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "error",
                                    e.getMessage() != null
                                            ? e.getMessage()
                                            : "Recovery execution failed"
                            )
                    );
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAttempt(
            @PathVariable Long id) {

        recoveryAttemptService.deleteAttempt(id);

        return ResponseEntity.noContent().build();
    }
}
