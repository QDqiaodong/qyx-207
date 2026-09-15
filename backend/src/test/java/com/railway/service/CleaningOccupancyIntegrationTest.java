package com.railway.service;

import com.railway.dto.BenchSitDTO;
import com.railway.dto.CleaningStartDTO;
import com.railway.entity.BenchSitting;
import com.railway.entity.CleaningOccupancy;
import com.railway.entity.RailwayLine;
import com.railway.entity.RestBench;
import com.railway.entity.Station;
import com.railway.repository.BenchSittingRepository;
import com.railway.repository.CleaningOccupancyRepository;
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
 * 清扫占台集成测试：真实 JPA（H2）验证占台单据落库、悲观锁串行、
 * 并发开清扫只留一套占台、占台不影响原坐客且新坐被拦、中途失败整体回滚。
 */
@DataJpaTest
@Import(BenchCleaningService.class)
class CleaningOccupancyIntegrationTest {

    @Autowired
    private BenchCleaningService benchCleaningService;
    @Autowired
    private RailwayLineRepository railwayLineRepository;
    @Autowired
    private StationRepository stationRepository;
    @Autowired
    private RestBenchRepository restBenchRepository;
    @Autowired
    private CleaningOccupancyRepository cleaningOccupancyRepository;
    @Autowired
    private BenchSittingRepository benchSittingRepository;
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

    private CleaningStartDTO startDto(Long... ids) {
        CleaningStartDTO dto = new CleaningStartDTO();
        dto.setBenchIds(List.of(ids));
        dto.setReason("集成测试清扫");
        return dto;
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void startKeepsExistingSitterAndBlocksNewSitters() {
        RailwayLine line = newLine("CL1");
        Station station = newStation("CS1", line);
        RestBench bench = newBench("CB1", line, station);

        // 清扫前已有客人在座
        BenchSitting sitter = benchCleaningService.sit(bench.getId(), new BenchSitDTO("张三", "p-zhang"));
        assertNotNull(sitter.getId());

        // 开清扫：占台单生效
        List<CleaningOccupancy> created = benchCleaningService.startCleaning(startDto(bench.getId()));
        assertEquals(1, created.size());
        assertEquals(1, cleaningOccupancyRepository.findByBenchIdAndStatus(bench.getId(), 1).orElseThrow().getStatus());

        // 原坐客仍在座（没有被清走），且归属仍是原台
        BenchSitting stillThere = benchSittingRepository.findByBenchId(bench.getId()).orElseThrow();
        assertEquals(sitter.getId(), stillThere.getId());
        assertEquals(bench.getId(), stillThere.getBench().getId());
        assertEquals("张三", stillThere.getPassengerName());

        // 新客人不能再坐
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> benchCleaningService.sit(bench.getId(), new BenchSitDTO("李四", "p-li")));
        assertTrue(ex.getMessage().contains("清扫占台中"));
        // 在座名单仍只有原坐客一个人，座位状态没有被改乱
        assertEquals(1, benchSittingRepository.findAllByOrderBySatAtDesc().size());

        // 原坐客自行离开后，仍不接新坐（清扫未结束）
        benchCleaningService.leave(sitter.getId());
        assertTrue(benchSittingRepository.findByBenchId(bench.getId()).isEmpty());
        RuntimeException ex2 = assertThrows(RuntimeException.class,
                () -> benchCleaningService.sit(bench.getId(), new BenchSitDTO("王五", "p-wang")));
        assertTrue(ex2.getMessage().contains("清扫占台中"));

        // 清扫结束后台子放开，新客人可以坐
        benchCleaningService.finishCleaning(created.get(0).getId());
        BenchSitting next = benchCleaningService.sit(bench.getId(), new BenchSitDTO("赵六", "p-zhao"));
        assertNotNull(next.getId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void startOnlyLocksNamedBenchesNotWholeLine() {
        RailwayLine line = newLine("CL2");
        Station station = newStation("CS2", line);
        RestBench dirty = newBench("CB21", line, station);
        RestBench clean = newBench("CB22", line, station);

        benchCleaningService.startCleaning(startDto(dirty.getId()));

        // 只锁点名的脏台：同站另一张干净台没有占台单，新客人照常能坐
        assertTrue(cleaningOccupancyRepository.findByBenchIdAndStatus(clean.getId(), 1).isEmpty());
        BenchSitting sitter = benchCleaningService.sit(clean.getId(), new BenchSitDTO("钱七", "p-qian"));
        assertNotNull(sitter.getId());
        // 被点名的脏台接不到新坐
        assertThrows(RuntimeException.class,
                () -> benchCleaningService.sit(dirty.getId(), new BenchSitDTO("孙八", "p-sun")));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedStartRollsBackEverything() {
        RailwayLine line = newLine("CL3");
        Station station = newStation("CS3", line);
        RestBench good = newBench("CB31", line, station);
        RestBench deleted = newBench("CB32", line, station);
        deleted.setStatus(0);
        restBenchRepository.save(deleted);

        // 点名两张台，其中第二张已删除：整单失败
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> benchCleaningService.startCleaning(startDto(good.getId(), deleted.getId())));
        assertTrue(ex.getMessage().contains("已删除"));

        // 没有留下任何半截占台单
        assertTrue(cleaningOccupancyRepository.findAll().isEmpty());
        // 台的编号、所属站仍是原样
        RestBench goodAfter = restBenchRepository.findById(good.getId()).orElseThrow();
        assertEquals("CB31", goodAfter.getBenchCode());
        assertEquals(station.getId(), goodAfter.getStation().getId());
        assertEquals(1, goodAfter.getStatus());
        RestBench deletedAfter = restBenchRepository.findById(deleted.getId()).orElseThrow();
        assertEquals("CB32", deletedAfter.getBenchCode());
        assertEquals(0, deletedAfter.getStatus());
        // 没有产生任何就座记录
        assertTrue(benchSittingRepository.findAll().isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentStartOnSameBenchYieldsSingleOccupancy() throws Exception {
        RailwayLine line = newLine("CL4");
        Station station = newStation("CS4", line);
        RestBench bench = newBench("CB41", line, station);
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
                    benchCleaningService.startCleaning(startDto(benchId));
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

        // 只能留下一套占台：一个成功，另一个被拒
        assertEquals(1, results.stream().filter("OK"::equals).count(), "结果: " + results);
        assertTrue(results.stream().anyMatch(r -> r.contains("已有生效清扫占台单")), "结果: " + results);
        List<CleaningOccupancy> active = cleaningOccupancyRepository.findByStatusOrderByStartedAtDesc(1);
        assertEquals(1, active.size());
        // 座位状态没被改乱：台仍在用、编号所属站原样，且没有任何就座记录被改动
        RestBench after = restBenchRepository.findById(benchId).orElseThrow();
        assertEquals(1, after.getStatus());
        assertEquals("CB41", after.getBenchCode());
        assertEquals(station.getId(), after.getStation().getId());
        assertTrue(benchSittingRepository.findAll().isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentSitAndStartNeverInterleave() throws Exception {
        RailwayLine line = newLine("CL5");
        Station station = newStation("CS5", line);
        RestBench bench = newBench("CB51", line, station);
        Long benchId = bench.getId();

        // 两人同时操作同一张台：一个开清扫，一个尝试新坐。
        // 合法结局只有两种：占台先生效 -> 落座被拦；落座先成功 -> 开清扫成功且原坐客仍在座。
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<String> cleaner = pool.submit(() -> {
            ready.countDown();
            start.await();
            try {
                benchCleaningService.startCleaning(startDto(benchId));
                return "CLEAN_OK";
            } catch (RuntimeException e) {
                return "CLEAN_FAIL:" + e.getMessage();
            }
        });
        Future<String> sitter = pool.submit(() -> {
            ready.countDown();
            start.await();
            try {
                benchCleaningService.sit(benchId, new BenchSitDTO("周九", "p-zhou"));
                return "SIT_OK";
            } catch (RuntimeException e) {
                return "SIT_FAIL:" + e.getMessage();
            }
        });
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        String cr = cleaner.get(15, TimeUnit.SECONDS);
        String sr = sitter.get(15, TimeUnit.SECONDS);
        pool.shutdown();

        boolean cleaningWon = cr.equals("CLEAN_OK") && sr.startsWith("SIT_FAIL");
        boolean sitterWon = cr.equals("CLEAN_OK") && sr.equals("SIT_OK");
        assertTrue(cleaningWon || sitterWon, "cleaner=" + cr + ", sitter=" + sr);

        // 无论谁先：最多一套占台单；最多一个坐客；绝不可能“占台生效同时新坐插入”
        assertEquals(1, cleaningOccupancyRepository.findByStatusOrderByStartedAtDesc(1).size());
        List<BenchSitting> sittings = benchSittingRepository.findAllByOrderBySatAtDesc();
        assertTrue(sittings.size() <= 1);
        if (!sittings.isEmpty()) {
            assertEquals(benchId, sittings.get(0).getBench().getId());
        }
    }
}
