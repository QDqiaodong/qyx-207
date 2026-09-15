package com.railway.service;

import com.railway.dto.StationDTO;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 线路作废与站点挂接的集成测试：真实 JPA（H2）验证派生查询、
 * 悲观锁 JPQL 与并发作废只落一套结果。
 */
@DataJpaTest
@Import({RailwayLineService.class, StationService.class, RestBenchService.class})
class LineInvalidationIntegrationTest {

    @Autowired
    private RailwayLineService railwayLineService;
    @Autowired
    private StationService stationService;
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

    private RailwayLine newLine(String code, String name) {
        RailwayLine line = new RailwayLine();
        line.setLineCode(code);
        line.setLineName(name);
        line.setLineType("地铁");
        line.setStatus(1);
        return railwayLineRepository.save(line);
    }

    private Station newStation(String code, RailwayLine line, int status) {
        Station station = new Station();
        station.setStationCode(code);
        station.setStationName(code + "站");
        station.setLineOrder(1);
        station.setRailwayLine(line);
        station.setStatus(status);
        return stationRepository.save(station);
    }

    @Test
    void deleteBlockedWhileStationsRemainAndNothingChanges() {
        RailwayLine line = newLine("IT-L1", "集成1号线");
        Station station = newStation("IT-S1", line, 1);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> railwayLineService.deleteLine(line.getId()));
        assertTrue(ex.getMessage().contains("不能作废"));

        // 线路名称、编码、状态全部原样
        RailwayLine after = railwayLineRepository.findById(line.getId()).orElseThrow();
        assertEquals(1, after.getStatus());
        assertEquals("集成1号线", after.getLineName());
        assertEquals("IT-L1", after.getLineCode());

        // 站点没有被半截摘掉，仍挂在原线上
        Station stationAfter = stationRepository.findById(station.getId()).orElseThrow();
        assertEquals(1, stationAfter.getStatus());
        assertEquals(line.getId(), stationAfter.getRailwayLine().getId());
        assertEquals(1, stationRepository.findByRailwayLineIdAndStatusNot(line.getId(), 0).size());
    }

    @Test
    void suspendedStationAlsoBlocksInvalidation() {
        RailwayLine line = newLine("IT-L2", "集成2号线");
        newStation("IT-S2", line, 2); // 停运改造也算未删除

        assertThrows(RuntimeException.class, () -> railwayLineService.deleteLine(line.getId()));
        assertEquals(1, railwayLineRepository.findById(line.getId()).orElseThrow().getStatus());
    }

    @Test
    void deleteSucceedsAfterStationsCleared() {
        RailwayLine line = newLine("IT-L3", "集成3号线");
        Station station = newStation("IT-S3", line, 1);
        station.setStatus(0);
        stationRepository.save(station);

        railwayLineService.deleteLine(line.getId());

        RailwayLine after = railwayLineRepository.findById(line.getId()).orElseThrow();
        assertEquals(0, after.getStatus());
        assertEquals("集成3号线", after.getLineName());
        assertEquals("IT-L3", after.getLineCode());
        // 线路名单里不再出现
        assertTrue(railwayLineRepository.findByStatus(1).stream()
                .noneMatch(l -> l.getId().equals(line.getId())));
    }

    @Test
    void repeatedInvalidationRejected() {
        RailwayLine line = newLine("IT-L4", "集成4号线");
        railwayLineService.deleteLine(line.getId());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> railwayLineService.deleteLine(line.getId()));
        assertTrue(ex.getMessage().contains("已作废"));
    }

    @Test
    void benchesByLineEmptyAfterInvalidation() {
        RailwayLine line = newLine("IT-L5", "集成5号线");
        Station station = newStation("IT-S5", line, 1);
        RestBench bench = new RestBench();
        bench.setBenchCode("IT-B5");
        bench.setMaterial("石材");
        bench.setRailwayLine(line);
        bench.setStation(station);
        bench.setStatus(1);
        restBenchRepository.save(bench);

        // 作废前：按线路能查到在用休息台
        assertEquals(1, restBenchService.getBenchesByLineId(line.getId()).size());

        // 站点清空后作废
        station.setStatus(0);
        stationRepository.save(station);
        railwayLineService.deleteLine(line.getId());

        // 作废后：按这条线查休息台必须为空（休息台记录还挂在旧站上也不行）
        assertTrue(restBenchService.getBenchesByLineId(line.getId()).isEmpty());
    }

    @Test
    void stationCannotAttachToInvalidatedLine() {
        RailwayLine deadLine = newLine("IT-L6", "集成6号线");
        railwayLineService.deleteLine(deadLine.getId());

        // 新建站点挂到已作废线路：拒绝
        StationDTO dto = new StationDTO();
        dto.setStationCode("IT-S6");
        dto.setStationName("新站");
        dto.setLineOrder(1);
        dto.setLineId(deadLine.getId());
        dto.setStatus(1);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> stationService.createStation(dto));
        assertTrue(ex.getMessage().contains("已作废"));

        // 已有站点改挂到已作废线路：拒绝，且站点仍挂在原线
        RailwayLine liveLine = newLine("IT-L7", "集成7号线");
        Station station = newStation("IT-S7", liveLine, 1);
        StationDTO move = new StationDTO();
        move.setStationCode("IT-S7");
        move.setStationName("新站");
        move.setLineOrder(1);
        move.setLineId(deadLine.getId());
        move.setStatus(1);
        assertThrows(RuntimeException.class, () -> stationService.updateStation(station.getId(), move));
        assertEquals(liveLine.getId(),
                stationRepository.findById(station.getId()).orElseThrow().getRailwayLine().getId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentInvalidationYieldsSingleConsistentOutcome() throws Exception {
        RailwayLine line = newLine("IT-L8", "集成8号线");
        Long lineId = line.getId();

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
                    railwayLineService.deleteLine(lineId);
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

        // 只能拦成一套结果：一个成功，一个看到已作废；没有人把站摘空
        assertEquals(1, results.stream().filter("OK"::equals).count(), "结果: " + results);
        assertTrue(results.stream().anyMatch(r -> r.contains("已作废")), "结果: " + results);
        assertEquals(0, railwayLineRepository.findById(lineId).orElseThrow().getStatus());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentInvalidationWithStationsBothBlocked() throws Exception {
        RailwayLine line = newLine("IT-L9", "集成9号线");
        newStation("IT-S9", line, 1);
        Long lineId = line.getId();

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
                    railwayLineService.deleteLine(lineId);
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

        // 两人都看到同一份拒绝结果，线路与站点保持原样
        assertEquals(2, results.stream().filter(r -> r.startsWith("FAIL")).count(), "结果: " + results);
        RailwayLine after = railwayLineRepository.findById(lineId).orElseThrow();
        assertEquals(1, after.getStatus());
        assertEquals("集成9号线", after.getLineName());
        assertEquals("IT-L9", after.getLineCode());
        assertEquals(1, stationRepository.findByRailwayLineIdAndStatusNot(lineId, 0).size());
    }
}
