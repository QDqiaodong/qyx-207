package com.railway.controller;

import com.railway.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/cache")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RedisCacheController {

    private final RedisCacheService redisCacheService;

    @GetMapping("/bench-materials")
    public ResponseEntity<Set<Object>> getAllBenchMaterials() {
        return ResponseEntity.ok(redisCacheService.getAllBenchMaterials());
    }

    @GetMapping("/bench-count")
    public ResponseEntity<Map<String, Long>> getBenchCount() {
        Map<String, Long> result = new HashMap<>();
        result.put("count", redisCacheService.getBenchCount());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/bench-score/{benchId}")
    public ResponseEntity<Map<String, Double>> getBenchScore(@PathVariable Long benchId) {
        Map<String, Double> result = new HashMap<>();
        result.put("score", redisCacheService.getBenchScore(benchId));
        return ResponseEntity.ok(result);
    }
}
