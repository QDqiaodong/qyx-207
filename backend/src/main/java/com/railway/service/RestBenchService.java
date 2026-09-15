package com.railway.service;

import com.railway.dto.BenchTransferDTO;
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
        // 线路在用汇总口径：只统计在用，待转运台立即不计入
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
        RestBench bench = restBenchRepository.findById(id)
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
