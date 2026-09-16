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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RestBenchService {

    // 休息台状态：1=在用，2=待转运，0=已删除
    private static final int BENCH_STATUS_DELETED = 0;
    private static final int BENCH_STATUS_IN_USE = 1;
    private static final int BENCH_STATUS_PENDING_TRANSFER = 2;

    // 站点状态：1=正常运营
    private static final int STATION_STATUS_ACTIVE = 1;

    // 线路状态：1=正常，0=已作废
    private static final int LINE_STATUS_ACTIVE = 1;

    private final RestBenchRepository restBenchRepository;
    private final RailwayLineRepository railwayLineRepository;
    private final StationRepository stationRepository;
    private final ChangeRecordRepository changeRecordRepository;
    private final RedisCacheService redisCacheService;

    public List<RestBench> getAllBenches() {
        // 在用 + 待转运都可见，待转运台需要在此办理转运
        return restBenchRepository.findByStatusNot(BENCH_STATUS_DELETED);
    }

    public List<RestBench> getBenchesByLineId(Long lineId) {
        // 线路在用汇总口径：只统计在用，待转运台立即不计入；
        // 线路已作废（或不存在）时，按线路查休息台一律为空
        Optional<RailwayLine> line = railwayLineRepository.findById(lineId);
        if (line.isEmpty() || line.get().getStatus() == null || line.get().getStatus() != LINE_STATUS_ACTIVE) {
            return Collections.emptyList();
        }
        return restBenchRepository.findByRailwayLineIdAndStatus(lineId, BENCH_STATUS_IN_USE);
    }

    public List<RestBench> getBenchesByStationId(Long stationId) {
        return restBenchRepository.findByStationId(stationId);
    }

    public Optional<RestBench> getBenchById(Long id) {
        return restBenchRepository.findById(id);
    }

    public Optional<RestBench> getBenchByCode(String benchCode) {
        return restBenchRepository.findByBenchCode(benchCode);
    }

    @Transactional
    public RestBench createBench(RestBenchDTO dto) {
        if (restBenchRepository.existsByBenchCode(dto.getBenchCode())) {
            throw new RuntimeException("休息台编号已存在");
        }
        RailwayLine line = railwayLineRepository.findById(dto.getLineId())
                .orElseThrow(() -> new RuntimeException("线路不存在"));
        Station station = stationRepository.findById(dto.getStationId())
                .orElseThrow(() -> new RuntimeException("站点不存在"));
        RestBench bench = new RestBench();
        bench.setBenchCode(dto.getBenchCode());
        bench.setMaterial(dto.getMaterial());
        bench.setSpecification(dto.getSpecification());
        bench.setPositionDesc(dto.getPositionDesc());
        bench.setRailwayLine(line);
        bench.setStation(station);
        bench.setStatus(dto.getStatus());
        RestBench saved = restBenchRepository.save(bench);
        redisCacheService.cacheBenchMaterial(saved);
        return saved;
    }

    @Transactional
    public RestBench updateBench(Long id, RestBenchDTO dto) {
        // 与转运/冲正共用休息台行锁：编辑改线改站与冲正串行，台账链不会被并发写岔
        RestBench bench = restBenchRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("休息台不存在"));
        if (!bench.getBenchCode().equals(dto.getBenchCode()) &&
            restBenchRepository.existsByBenchCode(dto.getBenchCode())) {
            throw new RuntimeException("休息台编号已存在");
        }

        RailwayLine oldLine = bench.getRailwayLine();
        Station oldStation = bench.getStation();

        RailwayLine newLine = railwayLineRepository.findById(dto.getLineId())
                .orElseThrow(() -> new RuntimeException("线路不存在"));
        Station newStation = stationRepository.findById(dto.getStationId())
                .orElseThrow(() -> new RuntimeException("站点不存在"));

        boolean lineChanged = !oldLine.getId().equals(newLine.getId());
        boolean stationChanged = !oldStation.getId().equals(newStation.getId());

        if (lineChanged || stationChanged) {
            saveChangeRecord(bench, oldLine, newLine, oldStation, newStation, null);
        }

        bench.setBenchCode(dto.getBenchCode());
        bench.setMaterial(dto.getMaterial());
        bench.setSpecification(dto.getSpecification());
        bench.setPositionDesc(dto.getPositionDesc());
        bench.setRailwayLine(newLine);
        bench.setStation(newStation);
        bench.setStatus(dto.getStatus());
        RestBench saved = restBenchRepository.save(bench);
        redisCacheService.cacheBenchMaterial(saved);
        return saved;
    }

    /**
     * 待转运休息台转运：只能转到仍在运营、且属于所选线路的站点。
     * 台账、位置状态、材质参数缓存同一事务完成；任何一步失败整体回滚，
     * 休息台保持待转运，不会回到在用，也不会留下半截台账。
     */
    @Transactional
    public RestBench transferBench(Long id, BenchTransferDTO dto) {
        RestBench bench = restBenchRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("休息台不存在"));
        if (bench.getStatus() == null || bench.getStatus() != BENCH_STATUS_PENDING_TRANSFER) {
            throw new RuntimeException("仅待转运状态的休息台可办理转运");
        }

        RailwayLine newLine = railwayLineRepository.findById(dto.getLineId())
                .orElseThrow(() -> new RuntimeException("线路不存在"));
        Station newStation = stationRepository.findById(dto.getStationId())
                .orElseThrow(() -> new RuntimeException("站点不存在"));
        if (newStation.getStatus() == null || newStation.getStatus() != STATION_STATUS_ACTIVE) {
            throw new RuntimeException("目标站点未在运营，不能转入");
        }
        if (!newStation.getRailwayLine().getId().equals(newLine.getId())) {
            throw new RuntimeException("目标站点不属于所选线路");
        }

        RailwayLine oldLine = bench.getRailwayLine();
        Station oldStation = bench.getStation();

        String reason = StringUtils.hasText(dto.getReason())
                ? dto.getReason() : "站点停运改造，休息台转运";
        saveChangeRecord(bench, oldLine, newLine, oldStation, newStation, reason);

        bench.setRailwayLine(newLine);
        bench.setStation(newStation);
        bench.setStatus(BENCH_STATUS_IN_USE);
        RestBench saved = restBenchRepository.save(bench);

        // 材质参数缓存按新位置刷新；缓存异常会触发回滚，不会出现台账已写而缓存指旧站
        redisCacheService.removeBenchMaterial(id);
        redisCacheService.cacheBenchMaterial(saved);
        return saved;
    }

    /**
     * 台账冲正：台账只进不出，写错的变更记录不许改不许删，只能对它补一条反向记录把影响抵回来。
     * 口径：
     * 1. 原记录保留，打上"已被冲正"标记并指向冲正单；冲正单的新旧线路站点与原记录正好对调，
     *    记在同一张台名下，一眼能看出谁冲了谁；
     * 2. 只能冲正本台最近一条未被冲正的正式变更，中间隔着别的记录不许跳冲；
     *    已被冲正过的记录不能再冲，冲正单本身也不能再被冲正；
     * 3. 一张台同一时刻只允许一次冲正：先锁休息台行，再在锁内锁定读台账，并发冲正串行，
     *    后到的看到该记录已被冲正而失败；
     * 4. 冲正只动本台：只标记本台原记录、只往本台名下补冲正单、只把本台调回原记录变更前的
     *    线路站点，其他台的台账与位置一概不碰；
     * 5. 冲正单落库、原记录标记、休息台位置、材质缓存同一事务完成，任何一步失败整体回滚，
     *    台账不留半条反向记录，休息台保持冲正前的样子。
     */
    @Transactional
    public ChangeRecord reverseChangeRecord(Long recordId, ChangeRecordReverseDTO dto) {
        // 先取这条记录属于哪张台（标量查询，记录实体不进持久化上下文；
        // 一切判定都以持锁后的锁定读为准，这次查询的内容不做判定依据）
        Long benchId = changeRecordRepository.findBenchIdById(recordId)
                .orElseThrow(() -> new RuntimeException("变更记录不存在"));
        RestBench bench = restBenchRepository.findByIdForUpdate(benchId)
                .orElseThrow(() -> new RuntimeException("休息台不存在"));

        // 持台锁后锁定读该台全部台账：与并发冲正串行，能读到对方已提交的冲正结果
        List<ChangeRecord> records = changeRecordRepository.findByBenchIdForUpdateOrderByIdDesc(bench.getId());
        ChangeRecord target = records.stream()
                .filter(r -> r.getId().equals(recordId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("变更记录不存在"));

        if (target.getReversalOfId() != null) {
            throw new RuntimeException("冲正记录不能再被冲正");
        }
        if (target.getReversedById() != null) {
            throw new RuntimeException("该变更记录已被冲正，不能重复冲正");
        }
        ChangeRecord latestEffective = records.stream()
                .filter(r -> r.getReversalOfId() == null && r.getReversedById() == null)
                .findFirst()
                .orElse(null);
        if (latestEffective == null || !latestEffective.getId().equals(recordId)) {
            throw new RuntimeException("只能冲正该休息台最近一条未冲正的变更记录，中间隔着记录不许跳冲");
        }

        // 回到原记录变更前的线路站点；原线路/站点已不存在则整体失败回滚
        RailwayLine backLine = railwayLineRepository.findById(target.getOldLineId())
                .orElseThrow(() -> new RuntimeException("原线路不存在，无法冲正"));
        Station backStation = stationRepository.findById(target.getOldStationId())
                .orElseThrow(() -> new RuntimeException("原站点不存在，无法冲正"));

        // 反向记录：新旧两端与原记录对调，记在同一台名下
        ChangeRecord reversal = new ChangeRecord();
        reversal.setBenchId(bench.getId());
        reversal.setBenchCode(bench.getBenchCode());
        reversal.setChangeType("REVERSAL");
        reversal.setOldLineId(target.getNewLineId());
        reversal.setOldLineName(target.getNewLineName());
        reversal.setNewLineId(target.getOldLineId());
        reversal.setNewLineName(target.getOldLineName());
        reversal.setOldStationId(target.getNewStationId());
        reversal.setOldStationName(target.getNewStationName());
        reversal.setNewStationId(target.getOldStationId());
        reversal.setNewStationName(target.getOldStationName());
        reversal.setChangeReason(StringUtils.hasText(dto.getReason()) ? dto.getReason()
                : "冲正：撤销变更记录 #" + target.getId());
        reversal.setOperator("admin");
        reversal.setReversalOfId(target.getId());
        ChangeRecord saved = changeRecordRepository.save(reversal);

        // 原记录留着，只打上"已被哪条冲正"的标记
        target.setReversedById(saved.getId());
        changeRecordRepository.save(target);

        // 休息台按冲正结果回到它该在的线路站点
        bench.setRailwayLine(backLine);
        bench.setStation(backStation);
        restBenchRepository.save(bench);

        // 材质参数缓存按冲正后的位置刷新；缓存异常触发回滚，不留半截冲正
        redisCacheService.removeBenchMaterial(bench.getId());
        redisCacheService.cacheBenchMaterial(bench);
        return saved;
    }


    private void saveChangeRecord(RestBench bench, RailwayLine oldLine, RailwayLine newLine,
                                  Station oldStation, Station newStation, String reason) {
        boolean lineChanged = !oldLine.getId().equals(newLine.getId());
        boolean stationChanged = !oldStation.getId().equals(newStation.getId());
        ChangeRecord record = new ChangeRecord();
        record.setBenchId(bench.getId());
        record.setBenchCode(bench.getBenchCode());
        record.setChangeType(lineChanged && stationChanged ? "LINE_STATION_CHANGE" :
                             lineChanged ? "LINE_CHANGE" : "STATION_CHANGE");
        record.setOldLineId(oldLine.getId());
        record.setOldLineName(oldLine.getLineName());
        record.setNewLineId(newLine.getId());
        record.setNewLineName(newLine.getLineName());
        record.setOldStationId(oldStation.getId());
        record.setOldStationName(oldStation.getStationName());
        record.setNewStationId(newStation.getId());
        record.setNewStationName(newStation.getStationName());
        record.setChangeReason(reason);
        record.setOperator("admin");
        changeRecordRepository.save(record);
    }

    @Transactional
    public void deleteBench(Long id) {
        RestBench bench = restBenchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("休息台不存在"));
        bench.setStatus(BENCH_STATUS_DELETED);
        restBenchRepository.save(bench);
        redisCacheService.removeBenchMaterial(id);
    }

    public List<ChangeRecord> getChangeRecords(Long benchId) {
        if (benchId != null) {
            return changeRecordRepository.findByBenchIdOrderByCreatedAtDesc(benchId);
        }
        return changeRecordRepository.findAllByOrderByCreatedAtDesc();
    }
}
