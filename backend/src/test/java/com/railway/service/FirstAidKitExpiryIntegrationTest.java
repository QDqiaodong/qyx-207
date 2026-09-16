package com.railway.service;

import com.railway.dto.FirstAidKitDTO;
import com.railway.dto.KitExpiryDTO;
import com.railway.entity.FirstAidKit;
import com.railway.entity.LineKitStock;
import com.railway.entity.RailwayLine;
import com.railway.entity.Station;
import com.railway.entity.StationKitStock;
import com.railway.repository.FirstAidKitRepository;
import com.railway.repository.LineKitStockRepository;
import com.railway.repository.RailwayLineRepository;
import com.railway.repository.StationKitStockRepository;
import com.railway.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 急救箱药品到期集成测试：真实 JPA（H2）验证按箱记批次和到期日、
 * 到期日到了本站与线路两处可用箱数同事务各减一、没到期的不从可用里拿掉、
 * 两人抢写同一箱只成一次、写到一半中断两处计数仍是动手前的数。
 * 各用例真实提交不回滚（并发场景需要），方法间会互相看到数据；
 * 每例开跑前清掉箱子与两本台账，让断言只看本例。
 */
@DataJpaTest
@Import(FirstAidKitService.class)
class FirstAidKitExpiryIntegrationTest {

    @Autowired
    private FirstAidKitService firstAidKitService;
    @Autowired
    private RailwayLineRepository railwayLineRepository;
    @Autowired
    private StationRepository stationRepository;
    @Autowired
    private FirstAidKitRepository firstAidKitRepository;
    @Autowired
    private StationKitStockRepository stationKitStockRepository;
    @Autowired
    private LineKitStockRepository lineKitStockRepository;

    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void cleanDocs() {
        firstAidKitRepository.deleteAll();
        stationKitStockRepository.deleteAll();
        lineKitStockRepository.deleteAll();
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

    private FirstAidKitDTO registerDto(String kitCode, Long stationId, String batchNo, LocalDate expiryDate) {
        FirstAidKitDTO dto = new FirstAidKitDTO();
        dto.setKitCode(kitCode);
        dto.setStationId(stationId);
        dto.setBatchNo(batchNo);
        dto.setExpiryDate(expiryDate);
        return dto;
    }

    private KitExpiryDTO expiryDto(String batchNo, LocalDate expiryDate) {
        KitExpiryDTO dto = new KitExpiryDTO();
        dto.setBatchNo(batchNo);
        dto.setExpiryDate(expiryDate);
        return dto;
    }

    private int stationCount(Long stationId) {
        return stationKitStockRepository.findByStationId(stationId)
                .map(StationKitStock::getAvailableCount).orElse(0);
    }

    private int lineCount(Long lineId) {
        return lineKitStockRepository.findByLineId(lineId)
                .map(LineKitStock::getAvailableCount).orElse(0);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void registerRecordsBatchAndExpiryAndBumpsBothCounts() {
        RailwayLine line = newLine("EL1");
        Station s1 = newStation("ES11", line);
        Station s2 = newStation("ES12", line);

        // 按箱记下批次和到期日：将来日期的箱子上架即算可用
        FirstAidKit k1 = firstAidKitService.registerKit(
                registerDto("EK11", s1.getId(), "B2026-01", today.plusDays(90)));
        assertEquals("B2026-01", k1.getBatchNo());
        assertEquals(today.plusDays(90), k1.getExpiryDate());
        assertNotNull(k1.getExpiryRecordedAt());
        assertEquals(1, k1.getStatus());

        // 本站可用箱数 +1，按线路加起来的可用箱数也 +1
        assertEquals(1, stationCount(s1.getId()));
        assertEquals(1, lineCount(line.getId()));

        // 同线另一站再上一箱：线路侧加成 2，两站各记各的
        firstAidKitService.registerKit(registerDto("EK12", s2.getId(), null, null));
        assertEquals(1, stationCount(s1.getId()));
        assertEquals(1, stationCount(s2.getId()));
        assertEquals(2, lineCount(line.getId()));

        // 台账把站名、线名原样抄进留档
        StationKitStock stationRow = stationKitStockRepository.findByStationId(s1.getId()).orElseThrow();
        assertEquals("ES11站", stationRow.getStationName());
        assertEquals("EL1号线", stationRow.getLineName());
        assertEquals("EL1号线", lineKitStockRepository.findByLineId(line.getId()).orElseThrow().getLineName());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void registerAlreadyDueKitIsNotCounted() {
        RailwayLine line = newLine("EL2");
        Station station = newStation("ES2", line);

        // 到期日已到的箱子：建档即撤下，不许再按能用计
        FirstAidKit kit = firstAidKitService.registerKit(
                registerDto("EK2", station.getId(), "B2025-99", today.minusDays(1)));
        assertEquals(0, kit.getStatus());
        assertEquals("B2025-99", kit.getBatchNo());
        assertEquals(today.minusDays(1), kit.getExpiryDate());
        assertEquals(0, stationCount(station.getId()));
        assertEquals(0, lineCount(line.getId()));

        // 已登记过批次和到期日的箱子，再写直接被拒
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                firstAidKitService.writeExpiry(kit.getId(), expiryDto("B2026-01", today.plusDays(30))));
        assertTrue(ex.getMessage().contains("不能重复登记"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void dueExpiryWritePullsKitFromBothCounts() {
        RailwayLine line = newLine("EL3");
        Station s1 = newStation("ES31", line);
        Station s2 = newStation("ES32", line);
        RailwayLine otherLine = newLine("EL9");
        Station s3 = newStation("ES93", otherLine);
        FirstAidKit k1 = firstAidKitService.registerKit(registerDto("EK31", s1.getId(), null, null));
        firstAidKitService.registerKit(registerDto("EK32", s2.getId(), null, null));
        firstAidKitService.registerKit(registerDto("EK93", s3.getId(), null, null));
        assertEquals(2, lineCount(line.getId()));
        assertEquals(1, lineCount(otherLine.getId()));

        // 到期日到了：箱子撤下，本站与线路两处可用箱数一起减这一箱
        FirstAidKit written = firstAidKitService.writeExpiry(
                k1.getId(), expiryDto("B2026-03", today.minusDays(1)));
        assertEquals(0, written.getStatus());
        assertEquals("B2026-03", written.getBatchNo());
        assertEquals(today.minusDays(1), written.getExpiryDate());
        assertNotNull(written.getExpiryRecordedAt());

        // 只减这一箱：本站 1→0，本线 2→1；别站、别线一根指头不动
        assertEquals(0, stationCount(s1.getId()));
        assertEquals(1, stationCount(s2.getId()));
        assertEquals(1, lineCount(line.getId()));
        assertEquals(1, stationCount(s3.getId()));
        assertEquals(1, lineCount(otherLine.getId()));

        // 库里再核一遍：箱子状态字、本站台账、线路台账三处是一起落的
        FirstAidKit after = firstAidKitRepository.findById(k1.getId()).orElseThrow();
        assertEquals(0, after.getStatus());
        assertEquals(today.minusDays(1), after.getExpiryDate());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void futureExpiryWriteKeepsKitAvailable() {
        RailwayLine line = newLine("EL4");
        Station station = newStation("ES4", line);
        FirstAidKit kit = firstAidKitService.registerKit(registerDto("EK4", station.getId(), null, null));

        // 写上将来的到期日：批次和到期日落档，但箱子不能从可用里拿掉
        FirstAidKit written = firstAidKitService.writeExpiry(
                kit.getId(), expiryDto("B2026-04", today.plusDays(30)));
        assertEquals(1, written.getStatus());
        assertEquals("B2026-04", written.getBatchNo());
        assertEquals(today.plusDays(30), written.getExpiryDate());
        assertEquals(1, stationCount(station.getId()));
        assertEquals(1, lineCount(line.getId()));

        // 同一箱写第二遍：被拒，两处计数依旧不动
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                firstAidKitService.writeExpiry(kit.getId(), expiryDto("B2026-05", today.minusDays(1))));
        assertTrue(ex.getMessage().contains("不能重复登记"));
        assertEquals(1, stationCount(station.getId()));
        assertEquals(1, lineCount(line.getId()));
        FirstAidKit after = firstAidKitRepository.findById(kit.getId()).orElseThrow();
        assertEquals(1, after.getStatus());
        assertEquals("B2026-04", after.getBatchNo());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentExpiryWriteOnlyOneSucceeds() throws Exception {
        RailwayLine line = newLine("EL5");
        Station station = newStation("ES5", line);
        FirstAidKit kit = firstAidKitService.registerKit(registerDto("EK5", station.getId(), null, null));
        Long kitId = kit.getId();
        assertEquals(1, stationCount(station.getId()));
        assertEquals(1, lineCount(line.getId()));

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        String[] batches = {"B-A", "B-B"};
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String batchNo = batches[i];
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    firstAidKitService.writeExpiry(kitId, expiryDto(batchNo, today.minusDays(1)));
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

        // 两名站务抢着给同一箱写到期日：只成一次
        assertEquals(1, results.stream().filter("OK"::equals).count(), "结果: " + results);
        assertTrue(results.stream().anyMatch(r -> r.contains("不能重复登记")), "结果: " + results);
        // 两处可用箱数只减一遍，不会减成负数
        assertEquals(0, stationCount(station.getId()));
        assertEquals(0, lineCount(line.getId()));
        FirstAidKit after = firstAidKitRepository.findById(kitId).orElseThrow();
        assertEquals(0, after.getStatus());
        assertNotNull(after.getExpiryDate());
        assertNotNull(after.getExpiryRecordedAt());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void interruptedWriteRollsBackBothCounts() {
        RailwayLine line = newLine("EL6");
        Station station = newStation("ES6", line);
        FirstAidKit kit = firstAidKitService.registerKit(registerDto("EK6", station.getId(), null, null));
        assertEquals(1, stationCount(station.getId()));
        assertEquals(1, lineCount(line.getId()));

        // 模拟写到一半中断：线路侧台账缺失，本站台账已在事务里减过，
        // 走到线路侧时必然失败
        lineKitStockRepository.deleteAll();
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                firstAidKitService.writeExpiry(kit.getId(), expiryDto("B2026-06", today.minusDays(1))));
        assertTrue(ex.getMessage().contains("台账缺失"));

        // 整体回滚：本站台账仍是动手前的数，箱子上也不留半截日期
        assertEquals(1, stationCount(station.getId()));
        FirstAidKit after = firstAidKitRepository.findById(kit.getId()).orElseThrow();
        assertEquals(1, after.getStatus());
        assertNull(after.getBatchNo());
        assertNull(after.getExpiryDate());
        assertNull(after.getExpiryRecordedAt());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void invalidWritesAreRejectedWithoutTouchingCounts() {
        RailwayLine line = newLine("EL7");
        Station station = newStation("ES7", line);

        // 批次和到期日要填一起填
        assertThrows(RuntimeException.class, () ->
                firstAidKitService.registerKit(registerDto("EK71", station.getId(), "B2026-07", null)));
        assertThrows(RuntimeException.class, () ->
                firstAidKitService.registerKit(registerDto("EK71", station.getId(), null, today.plusDays(30))));
        assertTrue(firstAidKitRepository.findAll().isEmpty());

        FirstAidKit kit = firstAidKitService.registerKit(registerDto("EK71", station.getId(), null, null));
        // 箱编号唯一
        assertThrows(RuntimeException.class, () ->
                firstAidKitService.registerKit(registerDto("EK71", station.getId(), null, null)));
        // 批次、到期日一样都不能少；不存在的箱子写不了
        assertThrows(RuntimeException.class, () ->
                firstAidKitService.writeExpiry(kit.getId(), expiryDto("  ", today.minusDays(1))));
        assertThrows(RuntimeException.class, () ->
                firstAidKitService.writeExpiry(kit.getId(), expiryDto("B2026-07", null)));
        assertThrows(RuntimeException.class, () ->
                firstAidKitService.writeExpiry(999999L, expiryDto("B2026-07", today.minusDays(1))));

        // 全部拒绝后：箱子仍在可用里，两处计数仍是 1
        assertEquals(1, stationCount(station.getId()));
        assertEquals(1, lineCount(line.getId()));
        FirstAidKit after = firstAidKitRepository.findById(kit.getId()).orElseThrow();
        assertEquals(1, after.getStatus());
        assertNull(after.getExpiryDate());
    }
}
