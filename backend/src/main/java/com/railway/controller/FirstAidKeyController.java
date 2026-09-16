package com.railway.controller;

import com.railway.dto.FirstAidKeyDTO;
import com.railway.dto.KeyCheckoutDTO;
import com.railway.entity.FirstAidKey;
import com.railway.entity.KeyCheckout;
import com.railway.service.FirstAidKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 急救箱钥匙档案与领用登记接口。
 * 领用只接收点名的具体钥匙 id 列表：值班长可一次领空全线，
 * 站务只能领本站名下那一把。
 */
@RestController
@RequestMapping("/api/keys")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FirstAidKeyController {

    private final FirstAidKeyService firstAidKeyService;

    // 钥匙名单（含在库/在外状态字）
    @GetMapping
    public ResponseEntity<List<FirstAidKey>> getAllKeys() {
        return ResponseEntity.ok(firstAidKeyService.getAllKeys());
    }

    // 钥匙建档：登记编号与所属站
    @PostMapping
    public ResponseEntity<?> registerKey(@Valid @RequestBody FirstAidKeyDTO dto) {
        try {
            return ResponseEntity.ok(firstAidKeyService.registerKey(dto));
        } catch (RuntimeException e) {
            return errorBody(e);
        }
    }

    // 注销钥匙（在外的钥匙须先交还）
    @DeleteMapping("/{keyId}")
    public ResponseEntity<?> retireKey(@PathVariable Long keyId) {
        try {
            firstAidKeyService.retireKey(keyId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return errorBody(e);
        }
    }

    // 领用登记：body 传 {"keyIds": [具体钥匙id...], "borrower": "...", "role": "SUPERVISOR|STATION_STAFF", "stationId": 站务必传}
    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(@Valid @RequestBody KeyCheckoutDTO dto) {
        try {
            return ResponseEntity.ok(firstAidKeyService.checkout(dto));
        } catch (RuntimeException e) {
            return errorBody(e);
        }
    }

    // 交还钥匙：登记单结清、状态字翻回在库
    @PutMapping("/checkouts/{checkoutId}/return")
    public ResponseEntity<?> returnKey(@PathVariable Long checkoutId) {
        try {
            return ResponseEntity.ok(firstAidKeyService.returnKey(checkoutId));
        } catch (RuntimeException e) {
            return errorBody(e);
        }
    }

    // 当前未交还的全部登记单（谁拿着钥匙一眼可查）
    @GetMapping("/checkouts/active")
    public ResponseEntity<List<KeyCheckout>> getActiveCheckouts() {
        return ResponseEntity.ok(firstAidKeyService.getActiveCheckouts());
    }

    // 某把钥匙的领用历史
    @GetMapping("/{keyId}/checkouts")
    public ResponseEntity<List<KeyCheckout>> getCheckoutsByKey(@PathVariable Long keyId) {
        return ResponseEntity.ok(firstAidKeyService.getCheckoutsByKey(keyId));
    }

    private ResponseEntity<Map<String, String>> errorBody(RuntimeException e) {
        Map<String, String> error = new HashMap<>();
        error.put("message", e.getMessage());
        return ResponseEntity.badRequest().body(error);
    }
}
