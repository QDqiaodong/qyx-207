package com.railway.service;

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

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RestBenchService {

    private final RestBenchRepository restBenchRepository;
    private final RailwayLineRepository railwayLineRepository;
    private final StationRepository stationRepository;
    private final ChangeRecordRepository changeRecordRepository;
    private final RedisCacheService redisCacheService;

    public List<RestBench> getAllBenches() {
        return restBenchRepository.findByStatus(1);
    }

    public List<RestBench> getBenchesByLineId(Long lineId) {
        return restBenchRepository.findByRailwayLineId(lineId);
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
            record.setOperator("admin");
            changeRecordRepository.save(record);
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

    @Transactional
    public void deleteBench(Long id) {
        RestBench bench = restBenchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("休息台不存在"));
        bench.setStatus(0);
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
