package com.railway.service;

import com.railway.dto.StationDTO;
import com.railway.entity.RailwayLine;
import com.railway.entity.Station;
import com.railway.repository.RailwayLineRepository;
import com.railway.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StationService {

    private final StationRepository stationRepository;
    private final RailwayLineRepository railwayLineRepository;

    public List<Station> getAllStations() {
        return stationRepository.findByStatus(1);
    }

    public List<Station> getStationsByLineId(Long lineId) {
        return stationRepository.findByRailwayLineIdAndStatus(lineId, 1);
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
        RailwayLine line = railwayLineRepository.findById(dto.getLineId())
                .orElseThrow(() -> new RuntimeException("线路不存在"));
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
        Station station = stationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("站点不存在"));
        if (!station.getStationCode().equals(dto.getStationCode()) && 
            stationRepository.existsByStationCode(dto.getStationCode())) {
            throw new RuntimeException("站点编码已存在");
        }
        RailwayLine line = railwayLineRepository.findById(dto.getLineId())
                .orElseThrow(() -> new RuntimeException("线路不存在"));
        station.setStationCode(dto.getStationCode());
        station.setStationName(dto.getStationName());
        station.setLineOrder(dto.getLineOrder());
        station.setRailwayLine(line);
        station.setStatus(dto.getStatus());
        return stationRepository.save(station);
    }

    @Transactional
    public void deleteStation(Long id) {
        Station station = stationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("站点不存在"));
        station.setStatus(0);
        stationRepository.save(station);
    }
}
