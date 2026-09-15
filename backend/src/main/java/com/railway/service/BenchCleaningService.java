package com.railway.service;

import com.railway.dto.BenchSitDTO;
import com.railway.dto.CleaningStartDTO;
import com.railway.entity.BenchSitting;
import com.railway.entity.CleaningOccupancy;
import com.railway.entity.RestBench;
import com.railway.entity.Station;
import com.railway.entity.RailwayLine;
import com.railway.repository.BenchSittingRepository;
import com.railway.repository.CleaningOccupancyRepository;
import com.railway.repository.RestBenchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 清扫占台口径（按现场要求实现）：
 * 1. 开清扫必须点名具体休息台，只锁点名的脏台，不提供整线/整站一把梭入口；
 * 2. 占台单生效后，该台不允许新客人就座（落座接口直接拦），但绝不清走已在座的客人，
 *    客人可以一直坐到自行离开（离开只删就座记录，由客人自己触发）；
 * 3. 两人同时给同一张台开清扫：对台行加悲观写锁串行，先到的落一套生效占台单，
 *    后到的看到已有生效单被拒绝，只留一套，不会把座位状态改乱；
 * 4. 中途任何一步失败整体回滚：占台单一条不留，台的编号、所属站、原坐客全部原样。
 */
@Service
@RequiredArgsConstructor
public class BenchCleaningService {

    // 休息台状态：0=已删除
    private static final int BENCH_STATUS_DELETED = 0;

    // 占台单状态：1=清扫中（生效占台），2=已结束
    private static final int OCCUPANCY_ACTIVE = 1;
    private static final int OCCUPANCY_FINISHED = 2;

    private final RestBenchRepository restBenchRepository;
    private final CleaningOccupancyRepository cleaningOccupancyRepository;
    private final BenchSittingRepository benchSittingRepository;

    /**
     * 开清扫占台：为每张被点名的台落一套生效占台单。
     * 先按 id 升序逐台加悲观写锁（多台点名时避免互等死锁），
     * 持锁期间再查生效占台单，保证并发开清扫只落一套。
     * 本方法不改动休息台的编号、所属站，也不动任何就座记录（不清人）。
     */
    @Transactional
    public List<CleaningOccupancy> startCleaning(CleaningStartDTO dto) {
        List<Long> benchIds = dto.getBenchIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (benchIds.isEmpty()) {
            throw new RuntimeException("必须点名至少一张具体休息台");
        }

        // 第一段：持锁逐台校验，任何一张不合法直接抛错，整个事务回滚，不留下半截占台单
        List<RestBench> benches = new ArrayList<>();
        for (Long benchId : benchIds) {
            RestBench bench = restBenchRepository.findByIdForUpdate(benchId)
                    .orElseThrow(() -> new RuntimeException("休息台不存在: id=" + benchId));
            if (bench.getStatus() != null && bench.getStatus() == BENCH_STATUS_DELETED) {
                throw new RuntimeException("休息台「" + bench.getBenchCode() + "」已删除，不能开清扫");
            }
            benches.add(bench);
        }

        // 第二段：台锁持有期间查占台单，与并发开清扫/落座串行
        List<CleaningOccupancy> created = new ArrayList<>();
        for (RestBench bench : benches) {
            cleaningOccupancyRepository
                    .findByBenchIdAndStatus(bench.getId(), OCCUPANCY_ACTIVE)
                    .ifPresent(existing -> {
                        throw new RuntimeException(
                                "休息台「" + bench.getBenchCode() + "」已有生效清扫占台单，不能重复占台");
                    });
            created.add(cleaningOccupancyRepository.save(buildOccupancy(bench, dto.getReason())));
        }
        return created;
    }

    private CleaningOccupancy buildOccupancy(RestBench bench, String reason) {
        Station station = bench.getStation();
        RailwayLine line = bench.getRailwayLine();
        CleaningOccupancy occupancy = new CleaningOccupancy();
        occupancy.setBench(bench);
        // 台的编号、所属站、所属线原样抄进单据，单据独立留档
        occupancy.setBenchCode(bench.getBenchCode());
        occupancy.setStationId(station.getId());
        occupancy.setStationName(station.getStationName());
        occupancy.setLineId(line.getId());
        occupancy.setLineName(line.getLineName());
        occupancy.setStatus(OCCUPANCY_ACTIVE);
        occupancy.setReason(StringUtils.hasText(reason) ? reason : "车站清扫班日常清扫");
        occupancy.setOperator("admin");
        return occupancy;
    }

    /**
     * 结束清扫：占台单置为已结束、台子放开。仍有客人在座时不能结束，
     * 等客人自行离开后再结束——绝不通过结束占台把人清走。重复结束幂等。
     */
    @Transactional
    public CleaningOccupancy finishCleaning(Long occupancyId) {
        CleaningOccupancy occupancy = cleaningOccupancyRepository.findByIdForUpdate(occupancyId)
                .orElseThrow(() -> new RuntimeException("清扫占台单不存在"));
        if (occupancy.getStatus() != null && occupancy.getStatus() == OCCUPANCY_FINISHED) {
            return occupancy;
        }
        benchSittingRepository.findByBenchId(occupancy.getBench().getId()).ifPresent(sitting -> {
            throw new RuntimeException("休息台「" + occupancy.getBenchCode()
                    + "」仍有客人在座，等客人离开后再结束清扫（不能清人）");
        });
        occupancy.setStatus(OCCUPANCY_FINISHED);
        occupancy.setFinishedAt(LocalDateTime.now());
        return cleaningOccupancyRepository.save(occupancy);
    }

    /**
     * 新客人就座：先锁台，再查生效占台单。清扫占台中直接拒绝落座；
     * 台已有人在座也拒绝。与开清扫共用台行悲观锁，杜绝占台与新坐对穿。
     */
    @Transactional
    public BenchSitting sit(Long benchId, BenchSitDTO dto) {
        RestBench bench = restBenchRepository.findByIdForUpdate(benchId)
                .orElseThrow(() -> new RuntimeException("休息台不存在"));
        if (bench.getStatus() != null && bench.getStatus() == BENCH_STATUS_DELETED) {
            throw new RuntimeException("休息台「" + bench.getBenchCode() + "」已删除，不能就座");
        }
        cleaningOccupancyRepository.findByBenchIdAndStatus(benchId, OCCUPANCY_ACTIVE)
                .ifPresent(o -> {
                    throw new RuntimeException(
                            "休息台「" + bench.getBenchCode() + "」清扫占台中，暂不接待新客人就座");
                });
        benchSittingRepository.findByBenchId(benchId).ifPresent(s -> {
            throw new RuntimeException("休息台「" + bench.getBenchCode() + "」已有客人在座");
        });

        BenchSitting sitting = new BenchSitting();
        sitting.setBench(bench);
        sitting.setBenchCode(bench.getBenchCode());
        sitting.setPassengerName(StringUtils.hasText(dto.getPassengerName()) ? dto.getPassengerName() : null);
        sitting.setPassengerKey(dto.getPassengerKey());
        return benchSittingRepository.save(sitting);
    }

    /**
     * 客人自行离开：只删本人的就座记录。清扫占台中的台客人离开后仍然不接新坐，
     * 直到清扫结束；本接口不会替清扫班提前放人。
     */
    @Transactional
    public void leave(Long sittingId) {
        BenchSitting sitting = benchSittingRepository.findByIdForUpdate(sittingId)
                .orElseThrow(() -> new RuntimeException("就座记录不存在或客人已离开"));
        benchSittingRepository.delete(sitting);
    }

    public List<CleaningOccupancy> getActiveOccupancies() {
        return cleaningOccupancyRepository.findByStatusOrderByStartedAtDesc(OCCUPANCY_ACTIVE);
    }

    public List<CleaningOccupancy> getOccupanciesByBench(Long benchId) {
        return cleaningOccupancyRepository.findByBenchIdOrderByStartedAtDesc(benchId);
    }

    public List<BenchSitting> getAllSittings() {
        return benchSittingRepository.findAllByOrderBySatAtDesc();
    }
}
