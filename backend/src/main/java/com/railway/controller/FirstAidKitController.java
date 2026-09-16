package com.railway.controller;

import com.railway.dto.FirstAidKitDTO;
import com.railway.dto.KitExpiryDTO;
import com.railway.entity.FirstAidKit;
import com.railway.service.FirstAidKitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 急救箱药品档案与到期登记接口。
 * 每箱按箱记下批次和到期日；写上的到期日到了，本站可用箱数和
 * 按线路加起来的可用箱数同一事务各减一，只改箱子不动线路侧不算做完。
 */
@RestController
@RequestMapping("/api/kits")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FirstAidKitController {

    private final FirstAidKitService firstAidKitService;

    // 急救箱名单（含批次、到期日、可用/已撤下状态字）
    @GetMapping
    public ResponseEntity<List<FirstAidKit>> getAllKits() {
        return ResponseEntity.ok(firstAidKitService.getAllKits());
    }

    // 急救箱建档：登记箱编号与所属站；批次和到期日选填，要填一起填
    @PostMapping
    public ResponseEntity<?> registerKit(@Valid @RequestBody FirstAidKitDTO dto) {
        try {
            return ResponseEntity.ok(firstAidKitService.registerKit(dto));
        } catch (RuntimeException e) {
            return errorBody(e);
        }
    }

    // 给箱子写批次和到期日：body 传 {"batchNo": "...", "expiryDate": "YYYY-MM-DD"}
    // 每箱只成一次；到期日到了的，两处可用箱数同一事务各减一
    @PutMapping("/{kitId}/expiry")
    public ResponseEntity<?> writeExpiry(@PathVariable Long kitId, @Valid @RequestBody KitExpiryDTO dto) {
        try {
            return ResponseEntity.ok(firstAidKitService.writeExpiry(kitId, dto));
        } catch (RuntimeException e) {
            return errorBody(e);
        }
    }

    // 两处可用箱数对账：本站台账 + 线路台账一起端出来
    @GetMapping("/stocks")
    public ResponseEntity<Map<String, Object>> getStocks() {
        return ResponseEntity.ok(firstAidKitService.getStocks());
    }

    private ResponseEntity<Map<String, String>> errorBody(RuntimeException e) {
        Map<String, String> error = new HashMap<>();
        error.put("message", e.getMessage());
        return ResponseEntity.badRequest().body(error);
    }
}
