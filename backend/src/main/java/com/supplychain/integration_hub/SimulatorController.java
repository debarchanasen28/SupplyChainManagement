package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Admin controls for the System 2 simulator: status, start/stop toggle, and fire-one-scenario. */
@RestController
@RequestMapping("/api/simulator")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SimulatorController {

    private final System2ProcurementOrderGenerator generator;

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(Map.of("running", generator.isRunning()));
    }

    @PostMapping("/start")
    public ResponseEntity<?> start() {
        generator.setRunning(true);
        return ResponseEntity.ok(Map.of("running", true));
    }

    @PostMapping("/stop")
    public ResponseEntity<?> stop() {
        generator.setRunning(false);
        return ResponseEntity.ok(Map.of("running", false));
    }

    @PostMapping("/fire")
    public ResponseEntity<?> fire() {
        return ResponseEntity.ok(Map.of("result", generator.fireOnce()));
    }
}
