package com.railway.service;

import com.railway.dto.FirstAidKeyDTO;
import com.railway.dto.KeyCheckoutDTO;
import com.railway.entity.FirstAidKey;
import com.railway.entity.KeyCheckout;
import com.railway.entity.RailwayLine;
import com.railway.entity.Station;
import com.railway.repository.FirstAidKeyRepository;
import com.railway.repository.KeyCheckoutRepository;
import com.railway.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 急救箱钥匙领用口径（按现场要求实现）：
 * 1. 每领走一把钥匙落一份领用登记，记清钥匙编号、所属站、领用人、交出时刻；
 * 2. 同一把钥匙未交还前开不出第二份登记，拒绝时写明上一份是谁领走的；
 * 3. 值班长可一次领空全线，站务只能领本站名下那一把；
 * 4. 两人同时领同一把：对钥匙行加悲观写锁串行，未交还登记只落一条，
 *    钥匙状态字不会写成两份在外；
 * 5. 领用写到一半失败整体回滚：登记单一条不留，钥匙仍在库，
 *    编号、所属站原样，不留半截；
 * 6. 登记单与钥匙状态字同一事务一起落：只改状态字不落单（或只落单不改状态）
 *    都不算完成，失败一起回滚。
 */
@Service
@RequiredArgsConstructor
public class FirstAidKeyService {

    // 钥匙状态：1=在库，2=在外（领用未还），0=已注销
    private static final int KEY_IN_STOCK = 1;
    private static final int KEY_OUT = 2;
    private static final int KEY_RETIRED = 0;

    // 登记单状态：1=未交还，2=已交还
    private static final int CHECKOUT_OUT = 1;
    private static final int CHECKOUT_RETURNED = 2;

    // 领用人角色
    private static final String ROLE_SUPERVISOR = "SUPERVISOR";
    private static final String ROLE_STATION_STAFF = "STATION_STAFF";

    private final FirstAidKeyRepository firstAidKeyRepository;
    private final KeyCheckoutRepository keyCheckoutRepository;
    private final StationRepository stationRepository;

    /**
     * 钥匙建档：登记钥匙编号与所属站，所属线由站点带出。新钥匙一律在库。
     */
    @Transactional
    public FirstAidKey registerKey(FirstAidKeyDTO dto) {
        if (firstAidKeyRepository.existsByKeyCode(dto.getKeyCode())) {
            throw new RuntimeException("钥匙编号「" + dto.getKeyCode() + "」已存在");
        }
        Station station = stationRepository.findById(dto.getStationId())
                .orElseThrow(() -> new RuntimeException("所属站点不存在: id=" + dto.getStationId()));
        FirstAidKey key = new FirstAidKey();
        key.setKeyCode(dto.getKeyCode());
        key.setStation(station);
        key.setRailwayLine(station.getRailwayLine());
        key.setStatus(KEY_IN_STOCK);
        return firstAidKeyRepository.save(key);
    }

    /**
     * 领用登记：为每把被点名的钥匙落一份登记单，并把钥匙状态字翻成在外。
     * 先按 id 升序逐把加悲观写锁（多把领用时避免互等死锁），持锁期间再查
     * 未交还登记，保证两人同时领同一把只落一条。登记单与状态字同事务落库，
     * 任何一步失败整体回滚——钥匙仍在库，编号、所属站原样。
     */
    @Transactional
    public List<KeyCheckout> checkout(KeyCheckoutDTO dto) {
        List<Long> keyIds = dto.getKeyIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (keyIds.isEmpty()) {
            throw new RuntimeException("必须点名至少一把具体钥匙");
        }
        if (!StringUtils.hasText(dto.getBorrower())) {
            throw new RuntimeException("领用人不能为空");
        }
        String role = StringUtils.hasText(dto.getRole()) ? dto.getRole().trim() : "";
        if (!ROLE_SUPERVISOR.equals(role) && !ROLE_STATION_STAFF.equals(role)) {
            throw new RuntimeException("领用人角色只能是值班长或站务");
        }
        // 站务必须报自己所在站；值班长领全线不需要
        if (ROLE_STATION_STAFF.equals(role) && dto.getStationId() == null) {
            throw new RuntimeException("站务领用必须报本人所在站，且只能领本站名下的钥匙");
        }

        // 第一段：持锁逐把校验，任何一把不合法直接抛错，整个事务回滚，不留半截登记单
        List<FirstAidKey> keys = new ArrayList<>();
        for (Long keyId : keyIds) {
            FirstAidKey key = firstAidKeyRepository.findByIdForUpdate(keyId)
                    .orElseThrow(() -> new RuntimeException("钥匙不存在: id=" + keyId));
            if (key.getStatus() != null && key.getStatus() == KEY_RETIRED) {
                throw new RuntimeException("钥匙「" + key.getKeyCode() + "」已注销，不能领用");
            }
            // 站务只能领本站名下那一把；值班长不受此限，可一次领空全线
            if (ROLE_STATION_STAFF.equals(role) && !dto.getStationId().equals(key.getStation().getId())) {
                throw new RuntimeException("钥匙「" + key.getKeyCode() + "」不属于本站，站务只能领本站名下的钥匙");
            }
            keys.add(key);
        }

        // 第二段：钥匙锁持有期间查登记单，与并发领用串行
        List<KeyCheckout> created = new ArrayList<>();
        for (FirstAidKey key : keys) {
            keyCheckoutRepository.findByKeyIdAndStatus(key.getId(), CHECKOUT_OUT)
                    .ifPresent(existing -> {
                        throw new RuntimeException("钥匙「" + key.getKeyCode() + "」尚未交还，上一份是「"
                                + existing.getBorrower() + "」于 " + existing.getCheckedOutAt()
                                + " 领走的，不能重复领用");
                    });
            created.add(keyCheckoutRepository.save(buildCheckout(key, dto.getBorrower().trim(), role)));
            // 状态字与登记单同一事务一起翻：在外
            key.setStatus(KEY_OUT);
            firstAidKeyRepository.save(key);
        }
        return created;
    }

    private KeyCheckout buildCheckout(FirstAidKey key, String borrower, String role) {
        Station station = key.getStation();
        RailwayLine line = key.getRailwayLine();
        KeyCheckout checkout = new KeyCheckout();
        checkout.setKey(key);
        // 钥匙编号、所属站、所属线原样抄进单据，单据独立留档
        checkout.setKeyCode(key.getKeyCode());
        checkout.setStationId(station.getId());
        checkout.setStationName(station.getStationName());
        checkout.setLineId(line.getId());
        checkout.setLineName(line.getLineName());
        checkout.setBorrower(borrower);
        checkout.setBorrowerRole(role);
        checkout.setStatus(CHECKOUT_OUT);
        return checkout;
    }

    /**
     * 交还钥匙：登记单置为已交还、记下交还时刻，钥匙状态字翻回在库，
     * 两者同一事务落库。重复交还幂等。
     */
    @Transactional
    public KeyCheckout returnKey(Long checkoutId) {
        KeyCheckout checkout = keyCheckoutRepository.findByIdForUpdate(checkoutId)
                .orElseThrow(() -> new RuntimeException("领用登记单不存在"));
        if (checkout.getStatus() != null && checkout.getStatus() == CHECKOUT_RETURNED) {
            return checkout;
        }
        FirstAidKey key = firstAidKeyRepository.findByIdForUpdate(checkout.getKey().getId())
                .orElseThrow(() -> new RuntimeException("钥匙不存在: id=" + checkout.getKey().getId()));
        checkout.setStatus(CHECKOUT_RETURNED);
        checkout.setReturnedAt(LocalDateTime.now());
        key.setStatus(KEY_IN_STOCK);
        firstAidKeyRepository.save(key);
        return keyCheckoutRepository.save(checkout);
    }

    /**
     * 注销钥匙：只许注销在库的钥匙；在外的钥匙先交还再注销，状态字置为已注销。
     */
    @Transactional
    public void retireKey(Long keyId) {
        FirstAidKey key = firstAidKeyRepository.findByIdForUpdate(keyId)
                .orElseThrow(() -> new RuntimeException("钥匙不存在: id=" + keyId));
        keyCheckoutRepository.findByKeyIdAndStatus(keyId, CHECKOUT_OUT).ifPresent(c -> {
            throw new RuntimeException("钥匙「" + key.getKeyCode() + "」尚未交还（「" + c.getBorrower()
                    + "」领走），先交还再注销");
        });
        key.setStatus(KEY_RETIRED);
        firstAidKeyRepository.save(key);
    }

    public List<FirstAidKey> getAllKeys() {
        return firstAidKeyRepository.findAllByOrderByKeyCodeAsc();
    }

    public List<KeyCheckout> getActiveCheckouts() {
        return keyCheckoutRepository.findByStatusOrderByCheckedOutAtDesc(CHECKOUT_OUT);
    }

    public List<KeyCheckout> getCheckoutsByKey(Long keyId) {
        return keyCheckoutRepository.findByKeyIdOrderByCheckedOutAtDesc(keyId);
    }
}
