package com.railway.service;

import com.railway.dto.StationDTO;
import com.railway.entity.RailwayLine;
import com.railway.entity.RestBench;
import com.railway.entity.Station;
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
public class StationService {

    // 站点状态：1=正常运营，2=停运改造，0=已删除
    private static final int STATION_STATUS_DELETED = 0;
    private static final int STATION_STATUS_ACTIVE = 1;
    private static final int STATION_STATUS_SUSPENDED = 2;

    // 线路状态：1=正常，0=已作废
    private static final int LINE_STATUS_ACTIVE = 1;

    // 休息台状态：1=在用，2=待转运，0=已删除
    private static final int BENCH_STATUS_IN_USE = 1;
    private static final int BENCH_STATUS_PENDING_TRANSFER = 2;

    private final StationRepository stationRepository;
    private final RailwayLineRepository railwayLineRepository;
    private final RestBenchRepository restBenchRepository;

    public List<Station> getAllStations() {
        return stationRepository.findByStatus(STATION_STATUS_ACTIVE);
    }

    public List<Station> getAllStationsIncludingSuspended() {
        return stationRepository.findByStatusNot(STATION_STATUS_DELETED);
    }

    public List<Station> getStationsByLineId(Long lineId) {
        return stationRepository.findByRailwayLineIdAndStatus(lineId, STATION_STATUS_ACTIVE);
    }

    public List<Station> getStationsByLineIdIncludingSuspended(Long lineId) {
        return stationRepository.findByRailwayLineIdAndStatusNot(lineId, STATION_STATUS_DELETED);
    }

    public Optional<Station> getStationById(Long id) {
        return stationRepository.findById(id);
    }

    public Optional<Station> getStationByCode(String stationCode) {
        return stationRepository.findByStationCode(stationCode);
    }

    @Transactional
    public Station createStation(StationDTO dto) {
        if (stationRepository.existsByStationCode(dto.getStationCode())) {
            throw new RuntimeException("站点编码已存在");
        }
        RailwayLine line = lockActiveLine(dto.getLineId());
        Station station = new Station();
        station.setStationCode(dto.getStationCode());
        station.setStationName(dto.getStationName());
        station.setLineOrder(dto.getLineOrder());
        station.setRailwayLine(line);
        station.setStatus(dto.getStatus());
        return stationRepository.save(station);
    }

    @Transactional
    public Station updateStation(Long id, StationDTO dto) {
        Station station = stationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("站点不存在"));
        if (!station.getStationCode().equals(dto.getStationCode()) &&
            stationRepository.existsByStationCode(dto.getStationCode())) {
            throw new RuntimeException("站点编码已存在");
        }
        RailwayLine line = lockActiveLine(dto.getLineId());
        // 编辑时把状态改为停运改造，与直接停运走同一套口径
        boolean toSuspend = dto.getStatus() != null
                && dto.getStatus() == STATION_STATUS_SUSPENDED
                && (station.getStatus() == null || station.getStatus() != STATION_STATUS_SUSPENDED);
        station.setStationCode(dto.getStationCode());
        station.setStationName(dto.getStationName());
        station.setLineOrder(dto.getLineOrder());
        station.setRailwayLine(line);
        station.setStatus(dto.getStatus());
        if (toSuspend) {
            markInUseBenchesPendingTransfer(station);
        }
        return stationRepository.save(station);
    }

    /**
     * 站点停运改造：允许站点先停，该站仍在用的休息台立即转入待转运，
     * 从线路在用汇总中移除。对站点行加悲观锁，并发重复停运幂等，
     * 待转运只落一套，不会把台子重新置回在用。
     */
    @Transactional
    public Station suspendStation(Long id) {
        Station station = stationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("站点不存在"));
        if (station.getStatus() != null && station.getStatus() == STATION_STATUS_DELETED) {
            throw new RuntimeException("站点已删除，不能标记停运改造");
        }
        if (station.getStatus() != null && station.getStatus() == STATION_STATUS_SUSPENDED) {
            // 已是停运改造：幂等返回，不重复处理休息台
            return station;
        }
        station.setStatus(STATION_STATUS_SUSPENDED);
        markInUseBenchesPendingTransfer(station);
        return stationRepository.save(station);
    }

    private void markInUseBenchesPendingTransfer(Station station) {
        List<RestBench> inUseBenches =
                restBenchRepository.findByStationIdAndStatus(station.getId(), BENCH_STATUS_IN_USE);
        for (RestBench bench : inUseBenches) {
            bench.setStatus(BENCH_STATUS_PENDING_TRANSFER);
            restBenchRepository.save(bench);
        }
    }

    @Transactional
    public void deleteStation(Long id) {
        Station station = stationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("站点不存在"));
        station.setStatus(STATION_STATUS_DELETED);
        stationRepository.save(station);
    }

    /**
     * 站点挂接/改挂前校验目标线路：对线路行加悲观锁，与线路作废串行，
     * 线路已作废（或不存在）时拒绝挂接，不会出现作废瞬间站点又挂上旧线。
     */
    private RailwayLine lockActiveLine(Long lineId) {
        RailwayLine line = railwayLineRepository.findByIdForUpdate(lineId)
                .orElseThrow(() -> new RuntimeException("线路不存在"));
        if (line.getStatus() == null || line.getStatus() != LINE_STATUS_ACTIVE) {
            throw new RuntimeException("线路已作废，不能挂载站点");
        }
        return line;
    }
}
