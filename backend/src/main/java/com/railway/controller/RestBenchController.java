package com.railway.controller;

import com.railway.dto.BenchTransferDTO;
import com.railway.dto.ChangeRecordReverseDTO;
import com.railway.dto.RestBenchDTO;
import com.railway.entity.ChangeRecord;
import com.railway.entity.RestBench;
import com.railway.service.RestBenchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/benches")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RestBenchController {

    private final RestBenchService restBenchService;

    @GetMapping
    public ResponseEntity<List<RestBench>> getAllBenches() {
        return ResponseEntity.ok(restBenchService.getAllBenches());
    }

    @GetMapping("/line/{lineId}")
    public ResponseEntity<List<RestBench>> getBenchesByLineId(@PathVariable Long lineId) {
        return ResponseEntity.ok(restBenchService.getBenchesByLineId(lineId));
    }

    @GetMapping("/station/{stationId}")
    public ResponseEntity<List<RestBench>> getBenchesByStationId(@PathVariable Long stationId) {
        return ResponseEntity.ok(restBenchService.getBenchesByStationId(stationId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RestBench> getBenchById(@PathVariable Long id) {
        return restBenchService.getBenchById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createBench(@Valid @RequestBody RestBenchDTO dto) {
        try {
            RestBench bench = restBenchService.createBench(dto);
            return ResponseEntity.ok(bench);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateBench(@PathVariable Long id, @Valid @RequestBody RestBenchDTO dto) {
        try {
            RestBench bench = restBenchService.updateBench(id, dto);
            return ResponseEntity.ok(bench);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/{id}/transfer")
    public ResponseEntity<?> transferBench(@PathVariable Long id, @Valid @RequestBody BenchTransferDTO dto) {
        try {
            RestBench bench = restBenchService.transferBench(id, dto);
            return ResponseEntity.ok(bench);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBench(@PathVariable Long id) {
        try {
            restBenchService.deleteBench(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/records")
    public ResponseEntity<List<ChangeRecord>> getChangeRecords(@RequestParam(required = false) Long benchId) {
        return ResponseEntity.ok(restBenchService.getChangeRecords(benchId));
    }

    @PostMapping("/records/{recordId}/reverse")
    public ResponseEntity<?> reverseChangeRecord(@PathVariable Long recordId,
                                                 @RequestBody(required = false) ChangeRecordReverseDTO dto) {
        try {
            ChangeRecord reversal = restBenchService.reverseChangeRecord(recordId,
                    dto != null ? dto : new ChangeRecordReverseDTO());
            return ResponseEntity.ok(reversal);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
