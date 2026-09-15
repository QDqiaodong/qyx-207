package com.railway.controller;

import com.railway.dto.BenchSitDTO;
import com.railway.dto.CleaningStartDTO;
import com.railway.entity.BenchSitting;
import com.railway.entity.CleaningOccupancy;
import com.railway.service.BenchCleaningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 清扫占台与就座接口。
 * 开清扫只接收点名的具体休息台 id 列表，没有按线路/按站点整把锁的入口。
 */
@RestController
@RequestMapping("/api/benches")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BenchCleaningController {

    private final BenchCleaningService benchCleaningService;

    // 开清扫占台：body 传 {"benchIds": [具体台id...], "reason": "..."}
    @PostMapping("/cleaning/start")
    public ResponseEntity<?> startCleaning(@Valid @RequestBody CleaningStartDTO dto) {
        try {
            List<CleaningOccupancy> occupancies = benchCleaningService.startCleaning(dto);
            return ResponseEntity.ok(occupancies);
        } catch (RuntimeException e) {
            return errorBody(e);
        }
    }

    // 结束清扫、放开台
    @PutMapping("/cleaning/{occupancyId}/finish")
    public ResponseEntity<?> finishCleaning(@PathVariable Long occupancyId) {
        try {
            return ResponseEntity.ok(benchCleaningService.finishCleaning(occupancyId));
        } catch (RuntimeException e) {
            return errorBody(e);
        }
    }

    // 当前生效（清扫中）的全部占台单
    @GetMapping("/cleaning/active")
    public ResponseEntity<List<CleaningOccupancy>> getActiveOccupancies() {
        return ResponseEntity.ok(benchCleaningService.getActiveOccupancies());
    }

    // 某张台的占台单历史
    @GetMapping("/{benchId}/cleaning")
    public ResponseEntity<List<CleaningOccupancy>> getOccupanciesByBench(@PathVariable Long benchId) {
        return ResponseEntity.ok(benchCleaningService.getOccupanciesByBench(benchId));
    }

    // 新客人落座：清扫占台中的台会被拒绝
    @PostMapping("/{benchId}/sit")
    public ResponseEntity<?> sit(@PathVariable Long benchId, @Valid @RequestBody BenchSitDTO dto) {
        try {
            return ResponseEntity.ok(benchCleaningService.sit(benchId, dto));
        } catch (RuntimeException e) {
            return errorBody(e);
        }
    }

    // 客人自行离开
    @DeleteMapping("/sittings/{sittingId}")
    public ResponseEntity<?> leave(@PathVariable Long sittingId) {
        try {
            benchCleaningService.leave(sittingId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return errorBody(e);
        }
    }

    // 当前在座名单（用于现场核对占台期间原坐客仍在座）
    @GetMapping("/sittings")
    public ResponseEntity<List<BenchSitting>> getAllSittings() {
        return ResponseEntity.ok(benchCleaningService.getAllSittings());
    }

    private ResponseEntity<Map<String, String>> errorBody(RuntimeException e) {
        Map<String, String> error = new HashMap<>();
        error.put("message", e.getMessage());
        return ResponseEntity.badRequest().body(error);
    }
}
