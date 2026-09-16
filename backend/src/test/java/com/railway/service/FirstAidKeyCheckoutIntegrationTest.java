package com.railway.service;

import com.railway.dto.KeyCheckoutDTO;
import com.railway.entity.FirstAidKey;
import com.railway.entity.KeyCheckout;
import com.railway.entity.RailwayLine;
import com.railway.entity.Station;
import com.railway.repository.FirstAidKeyRepository;
import com.railway.repository.KeyCheckoutRepository;
import com.railway.repository.RailwayLineRepository;
import com.railway.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 急救箱钥匙领用集成测试：真实 JPA（H2）验证领用登记四要素落库、
 * 未交还只此一份、值班长领全线/站务只领本站、悲观锁并发串行、
 * 中途失败整体回滚、登记单与状态字同事务同落同回。
 */
@DataJpaTest
@Import(FirstAidKeyService.class)
class FirstAidKeyCheckoutIntegrationTest {

    @Autowired
    private FirstAidKeyService firstAidKeyService;
    @Autowired
    private RailwayLineRepository railwayLineRepository;
    @Autowired
    private StationRepository stationRepository;
    @Autowired
    private FirstAidKeyRepository firstAidKeyRepository;
    @Autowired
    private KeyCheckoutRepository keyCheckoutRepository;

    @BeforeEach
    void cleanDocs() {
        // 各用例真实提交不回滚（并发场景需要），方法间会互相看到数据；
        // 每例开跑前清掉登记单并把钥匙状态字翻回在库，让断言只看本例
        keyCheckoutRepository.deleteAll();
        firstAidKeyRepository.deleteAll();
    }

    private RailwayLine newLine(String code) {
        RailwayLine line = new RailwayLine();
        line.setLineCode(code);
        line.setLineName(code + "号线");
        line.setLineType("地铁");
        line.setStatus(1);
        return railwayLineRepository.save(line);
    }

    private Station newStation(String code, RailwayLine line) {
        Station station = new Station();
        station.setStationCode(code);
        station.setStationName(code + "站");
        station.setLineOrder(1);
        station.setRailwayLine(line);
        station.setStatus(1);
        return stationRepository.save(station);
    }

    private FirstAidKey newKey(String code, RailwayLine line, Station station) {
        FirstAidKey key = new FirstAidKey();
        key.setKeyCode(code);
        key.setRailwayLine(line);
        key.setStation(station);
        key.setStatus(1);
        return firstAidKeyRepository.save(key);
    }

    private KeyCheckoutDTO checkoutDto(String borrower, String role, Long stationId, Long... keyIds) {
        KeyCheckoutDTO dto = new KeyCheckoutDTO();
        dto.setKeyIds(List.of(keyIds));
        dto.setBorrower(borrower);
        dto.setRole(role);
        dto.setStationId(stationId);
        return dto;
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void checkoutWritesFullRecordAndFlipsStatus() {
        RailwayLine line = newLine("KL1");
        Station station = newStation("KS1", line);
        FirstAidKey key = newKey("KK1", line, station);

        List<KeyCheckout> created = firstAidKeyService.checkout(
                checkoutDto("张三", "STATION_STAFF", station.getId(), key.getId()));
        assertEquals(1, created.size());

        // 登记四要素：钥匙编号、所属站、领用人、交出时刻，一样不少
        KeyCheckout record = created.get(0);
        assertEquals("KK1", record.getKeyCode());
        assertEquals(station.getId(), record.getStationId());
        assertEquals("KS1站", record.getStationName());
        assertEquals("张三", record.getBorrower());
        assertNotNull(record.getCheckedOutAt());
        assertEquals(1, record.getStatus());

        // 状态字同步翻成在外（与登记单同一事务落库）
        FirstAidKey after = firstAidKeyRepository.findById(key.getId()).orElseThrow();
        assertEquals(2, after.getStatus());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void unreturnedKeyRejectsSecondCheckoutAndNamesPreviousBorrower() {
        RailwayLine line = newLine("KL2");
        Station station = newStation("KS2", line);
        FirstAidKey key = newKey("KK2", line, station);

        firstAidKeyService.checkout(checkoutDto("张三", "STATION_STAFF", station.getId(), key.getId()));

        // 未交还开不出第二条，且写明上一份是谁领走的
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                firstAidKeyService.checkout(checkoutDto("李四", "SUPERVISOR", null, key.getId())));
        assertTrue(ex.getMessage().contains("尚未交还"));
        assertTrue(ex.getMessage().contains("张三"));

        // 仍然只有一条未交还登记，状态字还是一份在外
        assertEquals(1, keyCheckoutRepository.findByStatusOrderByCheckedOutAtDesc(1).size());
        assertEquals(2, firstAidKeyRepository.findById(key.getId()).orElseThrow().getStatus());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void stationStaffCanOnlyTakeOwnStationKey() {
        RailwayLine line = newLine("KL3");
        Station own = newStation("KS31", line);
        Station other = newStation("KS32", line);
        FirstAidKey ownKey = newKey("KK31", line, own);
        FirstAidKey otherKey = newKey("KK32", line, other);

        // 站务领别站名下的钥匙：被拒
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                firstAidKeyService.checkout(checkoutDto("王五", "STATION_STAFF", own.getId(), otherKey.getId())));
        assertTrue(ex.getMessage().contains("不属于本站"));

        // 站务一次点两把（本站+别站）：整单失败，本站那把也不许半领出去
        assertThrows(RuntimeException.class, () ->
                firstAidKeyService.checkout(checkoutDto("王五", "STATION_STAFF", own.getId(),
                        ownKey.getId(), otherKey.getId())));
        assertTrue(keyCheckoutRepository.findAll().isEmpty());
        assertEquals(1, firstAidKeyRepository.findById(ownKey.getId()).orElseThrow().getStatus());
        assertEquals(1, firstAidKeyRepository.findById(otherKey.getId()).orElseThrow().getStatus());

        // 站务领本站名下那把：正常
        List<KeyCheckout> created = firstAidKeyService.checkout(
                checkoutDto("王五", "STATION_STAFF", own.getId(), ownKey.getId()));
        assertEquals(1, created.size());
        assertEquals(own.getId(), created.get(0).getStationId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void supervisorCanTakeWholeLineAtOnce() {
        RailwayLine line = newLine("KL4");
        Station s1 = newStation("KS41", line);
        Station s2 = newStation("KS42", line);
        FirstAidKey k1 = newKey("KK41", line, s1);
        FirstAidKey k2 = newKey("KK42", line, s2);
        FirstAidKey k3 = newKey("KK43", line, s2);

        // 值班长一次领空全线：跨站多把一把梭
        List<KeyCheckout> created = firstAidKeyService.checkout(
                checkoutDto("赵值班长", "SUPERVISOR", null, k1.getId(), k2.getId(), k3.getId()));
        assertEquals(3, created.size());
        for (KeyCheckout c : created) {
            assertEquals("赵值班长", c.getBorrower());
            assertNotNull(c.getCheckedOutAt());
        }
        assertEquals(3, keyCheckoutRepository.findByStatusOrderByCheckedOutAtDesc(1).size());
        for (FirstAidKey k : List.of(k1, k2, k3)) {
            assertEquals(2, firstAidKeyRepository.findById(k.getId()).orElseThrow().getStatus());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentCheckoutYieldsSingleRecord() throws Exception {
        RailwayLine line = newLine("KL5");
        Station station = newStation("KS5", line);
        FirstAidKey key = newKey("KK5", line, station);
        Long keyId = key.getId();

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        String[] borrowers = {"张三", "李四"};
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String borrower = borrowers[i];
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    firstAidKeyService.checkout(checkoutDto(borrower, "SUPERVISOR", null, keyId));
                    return "OK";
                } catch (RuntimeException e) {
                    return "FAIL:" + e.getMessage();
                }
            }));
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        List<String> results = new ArrayList<>();
        for (Future<String> f : futures) {
            results.add(f.get(15, TimeUnit.SECONDS));
        }
        pool.shutdown();

        // 未交还登记只能落一条：一个成功，另一个看到上一份已被领走而失败
        assertEquals(1, results.stream().filter("OK"::equals).count(), "结果: " + results);
        assertTrue(results.stream().anyMatch(r -> r.contains("尚未交还")), "结果: " + results);
        List<KeyCheckout> active = keyCheckoutRepository.findByStatusOrderByCheckedOutAtDesc(1);
        assertEquals(1, active.size());
        // 状态字只有一份在外，钥匙编号、所属站原样
        FirstAidKey after = firstAidKeyRepository.findById(keyId).orElseThrow();
        assertEquals(2, after.getStatus());
        assertEquals("KK5", after.getKeyCode());
        assertEquals(station.getId(), after.getStation().getId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedCheckoutRollsBackEverything() {
        RailwayLine line = newLine("KL6");
        Station station = newStation("KS6", line);
        FirstAidKey good = newKey("KK61", line, station);
        FirstAidKey taken = newKey("KK62", line, station);

        // 先把 KK62 领走，制造中途必然失败的场面
        firstAidKeyService.checkout(checkoutDto("张三", "SUPERVISOR", null, taken.getId()));

        // 值班长一次领两把，其中 KK62 未交还：整单失败
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                firstAidKeyService.checkout(checkoutDto("赵值班长", "SUPERVISOR", null,
                        good.getId(), taken.getId())));
        assertTrue(ex.getMessage().contains("尚未交还"));

        // 没有留下任何半截登记单：KK61 没有新单，KK62 仍只有张三那一份
        List<KeyCheckout> active = keyCheckoutRepository.findByStatusOrderByCheckedOutAtDesc(1);
        assertEquals(1, active.size());
        assertEquals("KK62", active.get(0).getKeyCode());
        assertEquals("张三", active.get(0).getBorrower());
        // 钥匙仍算在站上：KK61 在库，编号、所属站原样
        FirstAidKey goodAfter = firstAidKeyRepository.findById(good.getId()).orElseThrow();
        assertEquals(1, goodAfter.getStatus());
        assertEquals("KK61", goodAfter.getKeyCode());
        assertEquals(station.getId(), goodAfter.getStation().getId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void returnRestoresStockAndAllowsNewCheckout() {
        RailwayLine line = newLine("KL7");
        Station station = newStation("KS7", line);
        FirstAidKey key = newKey("KK7", line, station);

        KeyCheckout record = firstAidKeyService.checkout(
                checkoutDto("张三", "STATION_STAFF", station.getId(), key.getId())).get(0);

        // 交还：登记单结清、记下交还时刻，状态字翻回在库
        KeyCheckout returned = firstAidKeyService.returnKey(record.getId());
        assertEquals(2, returned.getStatus());
        assertNotNull(returned.getReturnedAt());
        assertEquals(1, firstAidKeyRepository.findById(key.getId()).orElseThrow().getStatus());
        assertTrue(keyCheckoutRepository.findByStatusOrderByCheckedOutAtDesc(1).isEmpty());

        // 重复交还幂等
        KeyCheckout again = firstAidKeyService.returnKey(record.getId());
        assertEquals(2, again.getStatus());

        // 交还后可以再领，新单四要素齐全
        KeyCheckout next = firstAidKeyService.checkout(
                checkoutDto("李四", "STATION_STAFF", station.getId(), key.getId())).get(0);
        assertEquals("李四", next.getBorrower());
        assertEquals("KK7", next.getKeyCode());
        assertEquals(2, firstAidKeyRepository.findById(key.getId()).orElseThrow().getStatus());
    }
}
