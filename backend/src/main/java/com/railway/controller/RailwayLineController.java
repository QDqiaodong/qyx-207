package com.railway.controller;

import com.railway.dto.RailwayLineDTO;
import com.railway.entity.RailwayLine;
import com.railway.service.RailwayLineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lines")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RailwayLineController {

    private final RailwayLineService railwayLineService;

    @GetMapping
    public ResponseEntity<List<RailwayLine>> getAllLines() {
        return ResponseEntity.ok(railwayLineService.getAllLines());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RailwayLine> getLineById(@PathVariable Long id) {
        return railwayLineService.getLineById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createLine(@Valid @RequestBody RailwayLineDTO dto) {
        try {
            RailwayLine line = railwayLineService.createLine(dto);
            return ResponseEntity.ok(line);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateLine(@PathVariable Long id, @Valid @RequestBody RailwayLineDTO dto) {
        try {
            RailwayLine line = railwayLineService.updateLine(id, dto);
            return ResponseEntity.ok(line);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteLine(@PathVariable Long id) {
        try {
            railwayLineService.deleteLine(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
