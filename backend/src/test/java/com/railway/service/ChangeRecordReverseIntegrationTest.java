package com.railway.service;

import com.railway.dto.BenchTransferDTO;
import com.railway.dto.ChangeRecordReverseDTO;
import com.railway.dto.RestBenchDTO;
import com.railway.entity.ChangeRecord;
import com.railway.entity.RailwayLine;
import com.railway.entity.RestBench;
import com.railway.entity.Station;
import com.railway.repository.ChangeRecordRepository;
import com.railway.repository.RailwayLineRepository;
import com.railway.repository.RestBenchRepository;
import com.railway.repository.StationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * 台账冲正集成测试：真实 JPA（H2）验证冲正只进不出（原记录留档+反向记录抵回）、
 * 只冲最近一条不许跳冲、已冲正/冲正单不能再冲、只动本台、
 * 并发冲正只成一次、中途失败整体回滚。
 * 各用例真实提交不回滚，断言都按 benchId 收口，互不干扰。
 */
@DataJpaTest
@Import(RestBenchService.class)
class ChangeRecordReverseIntegrationTest {

    @Autowired
    private RestBenchService restBenchService;
    @Autowired
    private RailwayLineRepository railwayLineRepository;
    @Autowired
    private StationRepository stationRepository;
    @Autowired
    private RestBenchRepository restBenchRepository;
    @Autowired
    private ChangeRecordRepository changeRecordRepository;
    @MockBean
    private RedisCacheService redisCacheService;

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

    private RestBench newBench(String code, RailwayLine line, Station station) {
        RestBench bench = new RestBench();
        bench.setBenchCode(code);
        bench.setMaterial("石材");
        bench.setRailwayLine(line);
        bench.setStation(station);
        bench.setStatus(1);
        return restBenchRepository.save(bench);
    }

    /** 走真实编辑接口改线改站，留下一条正式变更台账 */
    private void moveBench(RestBench bench, RailwayLine line, Station station) {
        RestBenchDTO dto = new RestBenchDTO();
        dto.setBenchCode(bench.getBenchCode());
        dto.setMaterial(bench.getMaterial());
        dto.setLineId(line.getId());
        dto.setStationId(station.getId());
        dto.setStatus(1);
        restBenchService.updateBench(bench.getId(), dto);
    }

    /** 该台台账按 id 升序（即落账顺序） */
    private List<ChangeRecord> recordsOf(Long benchId) {
        return changeRecordRepository.findByBenchId(benchId).stream()
                .sorted(Comparator.comparing(ChangeRecord::getId))
                .toList();
    }

    private RestBench benchAt(Long benchId) {
        return restBenchRepository.findById(benchId).orElseThrow();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void reverseTransferRecordMovesBenchBackAndMarksOriginal() {
        RailwayLine line1 = newLine("RV-L1");
        Station s1 = newStation("RV-S1", line1);
        RailwayLine line2 = newLine("RV-L2");
        Station s2 = newStation("RV-S2", line2);
        RestBench bench = newBench("RV-B1", line1, s1);
        // 站点停运改造，台子待转运
        bench.setStatus(2);
        restBenchRepository.save(bench);

        // 转运时选错了站点：转到了 line2/s2
        BenchTransferDTO transfer = new BenchTransferDTO();
        transfer.setLineId(line2.getId());
        transfer.setStationId(s2.getId());
        restBenchService.transferBench(bench.getId(), transfer);
        assertEquals(s2.getId(), benchAt(bench.getId()).getStation().getId());
        ChangeRecord wrong = recordsOf(bench.getId()).get(0);

        // 冲正：补一条反向记录把影响抵回来
        ChangeRecord reversal = restBenchService.reverseChangeRecord(
                wrong.getId(), new ChangeRecordReverseDTO("转运选错站点"));

        // 原记录留着，能一眼看出已被冲正、被哪条冲正
        ChangeRecord original = changeRecordRepository.findById(wrong.getId()).orElseThrow();
        assertEquals(reversal.getId(), original.getReversedById());
        assertNull(original.getReversalOfId());
        assertEquals("转运选错站点", reversal.getChangeReason());
        // 反向记录记在同一张台名下，新旧两端与原记录对调，并指回原记录
        assertEquals(bench.getId(), reversal.getBenchId());
        assertEquals("REVERSAL", reversal.getChangeType());
        assertEquals(wrong.getId(), reversal.getReversalOfId());
        assertNull(reversal.getReversedById());
        assertEquals(wrong.getNewLineId(), reversal.getOldLineId());
        assertEquals(wrong.getOldLineId(), reversal.getNewLineId());
        assertEquals(wrong.getNewStationId(), reversal.getOldStationId());
        assertEquals(wrong.getOldStationId(), reversal.getNewStationId());
        assertEquals(wrong.getNewStationName(), reversal.getOldStationName());
        // 休息台按冲正结果回到该在的线路站点
        RestBench after = benchAt(bench.getId());
        assertEquals(line1.getId(), after.getRailwayLine().getId());
        assertEquals(s1.getId(), after.getStation().getId());
        // 台账只进不出：原记录和冲正单都在
        assertEquals(2, recordsOf(bench.getId()).size());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cannotReverseOlderRecordOverNewerOnes() {
        RailwayLine line1 = newLine("RV-L3");
        Station s1 = newStation("RV-S3", line1);
        RailwayLine line2 = newLine("RV-L4");
        Station s2 = newStation("RV-S4", line2);
        RailwayLine line3 = newLine("RV-L5");
        Station s3 = newStation("RV-S5", line3);
        RestBench bench = newBench("RV-B2", line1, s1);
        moveBench(bench, line2, s2);   // R1: ->line2/s2
        moveBench(benchAt(bench.getId()), line3, s3); // R2: ->line3/s3
        List<ChangeRecord> records = recordsOf(bench.getId());
        assertEquals(2, records.size());
        Long olderId = records.get(0).getId();

        // 中间隔着 R2，不许跳冲 R1
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> restBenchService.reverseChangeRecord(olderId, new ChangeRecordReverseDTO(null)));
        assertTrue(ex.getMessage().contains("最近一条"), ex.getMessage());

        // 台账与休息台全部原样
        List<ChangeRecord> after = recordsOf(bench.getId());
        assertEquals(2, after.size());
        assertTrue(after.stream().allMatch(r -> r.getReversedById() == null && r.getReversalOfId() == null));
        RestBench benchAfter = benchAt(bench.getId());
        assertEquals(line3.getId(), benchAfter.getRailwayLine().getId());
        assertEquals(s3.getId(), benchAfter.getStation().getId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void reversedRecordCannotBeReversedAgainAndReversalIsTerminal() {
        RailwayLine line1 = newLine("RV-L6");
        Station s1 = newStation("RV-S6", line1);
        RailwayLine line2 = newLine("RV-L7");
        Station s2 = newStation("RV-S7", line2);
        RestBench bench = newBench("RV-B3", line1, s1);
        moveBench(bench, line2, s2);
        Long recordId = recordsOf(bench.getId()).get(0).getId();

        ChangeRecord reversal = restBenchService.reverseChangeRecord(recordId, new ChangeRecordReverseDTO(null));

        // 已被冲正过的记录不能再冲一次
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> restBenchService.reverseChangeRecord(recordId, new ChangeRecordReverseDTO(null)));
        assertTrue(ex.getMessage().contains("已被冲正"), ex.getMessage());
        // 冲正单本身也不能再被冲正
        RuntimeException ex2 = assertThrows(RuntimeException.class,
                () -> restBenchService.reverseChangeRecord(reversal.getId(), new ChangeRecordReverseDTO(null)));
        assertTrue(ex2.getMessage().contains("冲正记录不能再被冲正"), ex2.getMessage());

        // 台账仍只有原记录+冲正单两条，休息台位置没再被挪动
        assertEquals(2, recordsOf(bench.getId()).size());
        RestBench after = benchAt(bench.getId());
        assertEquals(line1.getId(), after.getRailwayLine().getId());
        assertEquals(s1.getId(), after.getStation().getId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void reversalsPopLatestChangeFirst() {
        RailwayLine line1 = newLine("RV-L8");
        Station s1 = newStation("RV-S8", line1);
        RailwayLine line2 = newLine("RV-L9");
        Station s2 = newStation("RV-S9", line2);
        RailwayLine line3 = newLine("RV-L10");
        Station s3 = newStation("RV-S10", line3);
        RestBench bench = newBench("RV-B4", line1, s1);
        moveBench(bench, line2, s2);                    // R1: line1/s1 -> line2/s2
        moveBench(benchAt(bench.getId()), line3, s3);   // R2: line2/s2 -> line3/s3
        List<ChangeRecord> records = recordsOf(bench.getId());
        Long r1 = records.get(0).getId();
        Long r2 = records.get(1).getId();

        // 先冲最近的 R2：台子回到 R1 的结果 line2/s2
        restBenchService.reverseChangeRecord(r2, new ChangeRecordReverseDTO(null));
        RestBench mid = benchAt(bench.getId());
        assertEquals(line2.getId(), mid.getRailwayLine().getId());
        assertEquals(s2.getId(), mid.getStation().getId());

        // R2 冲掉后 R1 成为最近一条未冲正变更，可以接着冲：台子回到最初 line1/s1
        restBenchService.reverseChangeRecord(r1, new ChangeRecordReverseDTO(null));
        RestBench end = benchAt(bench.getId());
        assertEquals(line1.getId(), end.getRailwayLine().getId());
        assertEquals(s1.getId(), end.getStation().getId());

        // 两条原记录都已被冲正，两条冲正单指向各自原记录
        List<ChangeRecord> all = recordsOf(bench.getId());
        assertEquals(4, all.size());
        assertEquals(2, all.stream().filter(r -> r.getReversalOfId() != null).count());
        assertEquals(2, all.stream().filter(r -> r.getReversedById() != null).count());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void reverseOnlyTouchesOwnBench() {
        RailwayLine line1 = newLine("RV-L11");
        Station s1 = newStation("RV-S11", line1);
        RailwayLine line2 = newLine("RV-L12");
        Station s2 = newStation("RV-S12", line2);
        RestBench benchA = newBench("RV-B5", line1, s1);
        RestBench benchB = newBench("RV-B6", line1, s1);
        moveBench(benchA, line2, s2);
        moveBench(benchB, line2, s2);
        Long recordA = recordsOf(benchA.getId()).get(0).getId();

        restBenchService.reverseChangeRecord(recordA, new ChangeRecordReverseDTO(null));

        // 本台：已冲正 + 冲正单，台子回到 line1/s1
        assertEquals(2, recordsOf(benchA.getId()).size());
        assertEquals(s1.getId(), benchAt(benchA.getId()).getStation().getId());
        // 别的台：台账一条不多一条不少、没有标记，位置原样
        List<ChangeRecord> bRecords = recordsOf(benchB.getId());
        assertEquals(1, bRecords.size());
        assertNull(bRecords.get(0).getReversedById());
        assertNull(bRecords.get(0).getReversalOfId());
        RestBench bAfter = benchAt(benchB.getId());
        assertEquals(line2.getId(), bAfter.getRailwayLine().getId());
        assertEquals(s2.getId(), bAfter.getStation().getId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentReverseSameRecordOnlyOneSucceeds() throws Exception {
        RailwayLine line1 = newLine("RV-L13");
        Station s1 = newStation("RV-S13", line1);
        RailwayLine line2 = newLine("RV-L14");
        Station s2 = newStation("RV-S14", line2);
        RestBench bench = newBench("RV-B7", line1, s1);
        moveBench(bench, line2, s2);
        Long recordId = recordsOf(bench.getId()).get(0).getId();
        Long benchId = bench.getId();

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    restBenchService.reverseChangeRecord(recordId, new ChangeRecordReverseDTO(null));
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

        // 只能成一次：一个成功，另一个看到这条已经被冲了
        assertEquals(1, results.stream().filter("OK"::equals).count(), "结果: " + results);
        assertTrue(results.stream().anyMatch(r -> r.contains("已被冲正")), "结果: " + results);
        // 台账只有原记录+一条冲正单，休息台回到变更前位置
        List<ChangeRecord> all = recordsOf(benchId);
        assertEquals(2, all.size());
        assertEquals(1, all.stream().filter(r -> r.getReversalOfId() != null).count());
        RestBench after = benchAt(benchId);
        assertEquals(line1.getId(), after.getRailwayLine().getId());
        assertEquals(s1.getId(), after.getStation().getId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void missingOriginalStationRollsBackEverything() {
        RailwayLine line1 = newLine("RV-L15");
        Station s1 = newStation("RV-S15", line1);
        RailwayLine line2 = newLine("RV-L16");
        Station s2 = newStation("RV-S16", line2);
        RestBench bench = newBench("RV-B8", line2, s2);
        // 手工落一条原站点已不存在的变更记录（旧站 id 指向不存在的站点）
        ChangeRecord bad = new ChangeRecord();
        bad.setBenchId(bench.getId());
        bad.setBenchCode(bench.getBenchCode());
        bad.setChangeType("LINE_STATION_CHANGE");
        bad.setOldLineId(line1.getId());
        bad.setOldLineName(line1.getLineName());
        bad.setNewLineId(line2.getId());
        bad.setNewLineName(line2.getLineName());
        bad.setOldStationId(999999L);
        bad.setOldStationName("已拆除站");
        bad.setNewStationId(s2.getId());
        bad.setNewStationName(s2.getStationName());
        bad.setOperator("admin");
        changeRecordRepository.save(bad);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> restBenchService.reverseChangeRecord(bad.getId(), new ChangeRecordReverseDTO(null)));
        assertTrue(ex.getMessage().contains("原站点不存在"), ex.getMessage());

        // 不留半条反向记录，原记录没被打标记，休息台保持冲正前的样子
        List<ChangeRecord> all = recordsOf(bench.getId());
        assertEquals(1, all.size());
        assertNull(all.get(0).getReversedById());
        RestBench after = benchAt(bench.getId());
        assertEquals(line2.getId(), after.getRailwayLine().getId());
        assertEquals(s2.getId(), after.getStation().getId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cacheFailureRollsBackEverything() {
        RailwayLine line1 = newLine("RV-L17");
        Station s1 = newStation("RV-S17", line1);
        RailwayLine line2 = newLine("RV-L18");
        Station s2 = newStation("RV-S18", line2);
        RestBench bench = newBench("RV-B9", line1, s1);
        moveBench(bench, line2, s2);
        Long recordId = recordsOf(bench.getId()).get(0).getId();

        // 冲正写到一半（冲正单已落库）缓存故障：整体回滚
        doThrow(new RuntimeException("缓存故障")).when(redisCacheService).cacheBenchMaterial(any());
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> restBenchService.reverseChangeRecord(recordId, new ChangeRecordReverseDTO(null)));
        assertTrue(ex.getMessage().contains("缓存故障"), ex.getMessage());

        // 台账不留半条反向记录，原记录无标记，休息台仍是冲正前的位置
        List<ChangeRecord> all = recordsOf(bench.getId());
        assertEquals(1, all.size());
        assertNull(all.get(0).getReversedById());
        assertNull(all.get(0).getReversalOfId());
        RestBench after = benchAt(bench.getId());
        assertEquals(line2.getId(), after.getRailwayLine().getId());
        assertEquals(s2.getId(), after.getStation().getId());
    }
}
