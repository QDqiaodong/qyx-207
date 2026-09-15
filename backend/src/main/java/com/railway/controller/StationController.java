package com.railway.controller;

import com.railway.dto.StationDTO;
import com.railway.entity.Station;
import com.railway.service.StationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stations")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StationController {

    private final StationService stationService;

    @GetMapping
    public ResponseEntity<List<Station>> getAllStations(
            @RequestParam(defaultValue = "false") boolean includeAll) {
        return ResponseEntity.ok(includeAll
                ? stationService.getAllStationsIncludingSuspended()
                : stationService.getAllStations());
    }

    @GetMapping("/line/{lineId}")
    public ResponseEntity<List<Station>> getStationsByLineId(
            @PathVariable Long lineId,
            @RequestParam(defaultValue = "false") boolean includeAll) {
        return ResponseEntity.ok(includeAll
                ? stationService.getStationsByLineIdIncludingSuspended(lineId)
                : stationService.getStationsByLineId(lineId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Station> getStationById(@PathVariable Long id) {
        return stationService.getStationById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createStation(@Valid @RequestBody StationDTO dto) {
        try {
            Station station = stationService.createStation(dto);
            return ResponseEntity.ok(station);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateStation(@PathVariable Long id, @Valid @RequestBody StationDTO dto) {
        try {
            Station station = stationService.updateStation(id, dto);
            return ResponseEntity.ok(station);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/{id}/suspend")
    public ResponseEntity<?> suspendStation(@PathVariable Long id) {
        try {
            Station station = stationService.suspendStation(id);
            return ResponseEntity.ok(station);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteStation(@PathVariable Long id) {
        try {
            stationService.deleteStation(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
