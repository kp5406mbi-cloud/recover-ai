package com.recoverai.simulation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/run")
    public ResponseEntity<SimulationResult> run(
            @RequestBody(required = false)
            SimulationRequest request) {

        Integer count =
                request == null ? null : request.count();

        return ResponseEntity.ok(
                simulationService.run(count)
        );
    }
}
